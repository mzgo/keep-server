import { Hono } from 'hono'
import type { Env, ContextVariables, PointsLedgerRow } from '../types'
import { authMiddleware, requireRole } from '../middleware/auth'
import { AppError } from '../middleware/error-handler'
import { generateId, generateToken } from '../utils/crypto'
import { getAvailablePoints, expirePoints } from '../services/points'

const prizes = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

prizes.use('*', authMiddleware)

// === 管理者：奖品 CRUD ===

// 创建奖品
prizes.post('/', requireRole('manager'), async (c) => {
  const managerId = c.get('userId')
  const formData = await c.req.formData()

  const name = formData.get('name') as string
  const pointsRequired = parseInt(formData.get('points_required') as string)
  const stock = parseInt(formData.get('stock') as string)
  const isBlindBox = formData.get('is_blind_box') === '1' ? 1 : 0
  const imageFile = formData.get('image') as File | null

  if (!name || isNaN(pointsRequired) || isNaN(stock)) {
    throw new AppError(400, '奖品名称、积分和库存为必填')
  }

  let imageKey: string | null = null
  if (imageFile && imageFile.size > 0) {
    const ext = imageFile.name.split('.').pop() || 'jpg'
    imageKey = `prizes/${managerId}/${generateId()}.${ext}`
    await c.env.R2.put(imageKey, imageFile.stream(), {
      httpMetadata: { contentType: imageFile.type },
    })
  }

  const id = generateId()
  const now = Date.now()

  await c.env.DB.prepare(
    `INSERT INTO prizes (id, manager_id, name, image_key, points_required, stock, is_blind_box, is_active, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)`
  ).bind(id, managerId, name, imageKey, pointsRequired, stock, isBlindBox, now, now).run()

  return c.json({ success: true, data: { id } })
})

// 更新奖品
prizes.put('/:id', requireRole('manager'), async (c) => {
  const managerId = c.get('userId')
  const prizeId = c.req.param('id')
  const formData = await c.req.formData()

  const prize = await c.env.DB.prepare(
    'SELECT id FROM prizes WHERE id = ? AND manager_id = ?'
  ).bind(prizeId, managerId).first()

  if (!prize) {
    throw new AppError(404, '奖品不存在')
  }

  const updates: string[] = []
  const values: any[] = []

  const name = formData.get('name') as string | null
  if (name) { updates.push('name = ?'); values.push(name) }

  const pointsRequired = formData.get('points_required') as string | null
  if (pointsRequired) { updates.push('points_required = ?'); values.push(parseInt(pointsRequired)) }

  const stock = formData.get('stock') as string | null
  if (stock) { updates.push('stock = ?'); values.push(parseInt(stock)) }

  const isBlindBox = formData.get('is_blind_box') as string | null
  if (isBlindBox !== null) { updates.push('is_blind_box = ?'); values.push(isBlindBox === '1' ? 1 : 0) }

  const isActive = formData.get('is_active') as string | null
  if (isActive !== null) { updates.push('is_active = ?'); values.push(isActive === '1' ? 1 : 0) }

  const imageFile = formData.get('image') as File | null
  if (imageFile && imageFile.size > 0) {
    const ext = imageFile.name.split('.').pop() || 'jpg'
    const imageKey = `prizes/${managerId}/${generateId()}.${ext}`
    await c.env.R2.put(imageKey, imageFile.stream(), {
      httpMetadata: { contentType: imageFile.type },
    })
    updates.push('image_key = ?')
    values.push(imageKey)
  }

  if (updates.length === 0) {
    throw new AppError(400, '没有要更新的字段')
  }

  updates.push('updated_at = ?')
  values.push(Date.now())
  values.push(prizeId)

  await c.env.DB.prepare(
    `UPDATE prizes SET ${updates.join(', ')} WHERE id = ?`
  ).bind(...values).run()

  return c.json({ success: true, message: '更新成功' })
})

// 管理者：奖品列表（含库存）
prizes.get('/manage', requireRole('manager'), async (c) => {
  const managerId = c.get('userId')
  const { results } = await c.env.DB.prepare(
    'SELECT * FROM prizes WHERE manager_id = ? ORDER BY created_at DESC'
  ).bind(managerId).all()

  const data = results.map((r: any) => ({
    ...r,
    image_url: r.image_key ? `/api/files/${r.image_key}` : null,
  }))

  return c.json({ success: true, data })
})

// === 打卡者：商城 ===

// 商城奖品列表
prizes.get('/shop', async (c) => {
  const userId = c.get('userId')

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const { results } = await c.env.DB.prepare(
    'SELECT id, name, image_key, points_required, stock, is_blind_box FROM prizes WHERE manager_id = ? AND is_active = 1 ORDER BY points_required ASC'
  ).bind(user.manager_id).all()

  const available = await getAvailablePoints(c.env, userId, user.manager_id)

  const data = results.map((r: any) => ({
    ...r,
    image_url: r.image_key ? `/api/files/${r.image_key}` : null,
    can_redeem: r.stock > 0 && available >= r.points_required,
    points_short: Math.max(0, r.points_required - available),
  }))

  return c.json({ success: true, data, available_points: available })
})

// 兑换奖品
prizes.post('/redeem/:id', async (c) => {
  const userId = c.get('userId')
  const prizeId = c.req.param('id')

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const prize = await c.env.DB.prepare(
    'SELECT * FROM prizes WHERE id = ? AND manager_id = ? AND is_active = 1'
  ).bind(prizeId, user.manager_id).first<any>()

  if (!prize) {
    throw new AppError(404, '奖品不存在或已下架')
  }

  const now = Date.now()

  // 乐观锁扣库存：条件更新确保 stock > 0，避免并发超扣
  const stockResult = await c.env.DB.prepare(
    'UPDATE prizes SET stock = stock - 1, updated_at = ? WHERE id = ? AND stock > 0'
  ).bind(now, prizeId).run()

  if (!stockResult.meta.changes) {
    throw new AppError(400, '库存不足')
  }

  // 过期处理
  await expirePoints(c.env, userId, user.manager_id)

  // 检查可用积分
  const available = await getAvailablePoints(c.env, userId, user.manager_id)
  if (available < prize.points_required) {
    // 积分不足，回滚库存
    await c.env.DB.prepare(
      'UPDATE prizes SET stock = stock + 1, updated_at = ? WHERE id = ?'
    ).bind(now, prizeId).run()
    throw new AppError(400, '积分不足')
  }

  const orderId = generateId()
  const verifyCode = generateToken().slice(0, 12).toUpperCase()

  // FIFO 消费积分，使用条件更新防止超扣
  const { results: ledgers } = await c.env.DB.prepare(
    `SELECT id, remaining_amount FROM points_ledger
     WHERE user_id = ? AND manager_id = ? AND remaining_amount > 0 AND expires_at > ?
     ORDER BY expires_at ASC`
  ).bind(userId, user.manager_id, now).all<Pick<PointsLedgerRow, 'id' | 'remaining_amount'>>()

  let remaining = prize.points_required
  const usages: Array<{ ledger_id: string; amount: number }> = []

  for (const ledger of ledgers) {
    if (remaining <= 0) break
    const consume = Math.min(remaining, ledger.remaining_amount)

    const result = await c.env.DB.prepare(
      'UPDATE points_ledger SET remaining_amount = remaining_amount - ? WHERE id = ? AND remaining_amount >= ?'
    ).bind(consume, ledger.id, consume).run()

    if (result.meta.changes) {
      usages.push({ ledger_id: ledger.id, amount: consume })
      remaining -= consume
    }
  }

  // 积分扣减不完整时回滚（理论上不会走到这里，因为前面已检查过）
  if (remaining > 0) {
    for (const usage of usages) {
      await c.env.DB.prepare(
        'UPDATE points_ledger SET remaining_amount = remaining_amount + ? WHERE id = ?'
      ).bind(usage.amount, usage.ledger_id).run()
    }
    await c.env.DB.prepare(
      'UPDATE prizes SET stock = stock + 1, updated_at = ? WHERE id = ?'
    ).bind(now, prizeId).run()
    throw new AppError(400, '积分不足，请重试')
  }

  // 创建订单
  await c.env.DB.prepare(
    `INSERT INTO orders (id, user_id, manager_id, prize_id, points_spent, status, verify_code, created_at)
     VALUES (?, ?, ?, ?, ?, 'pending', ?, ?)`
  ).bind(orderId, userId, user.manager_id, prizeId, prize.points_required, verifyCode, now).run()

  // 记录积分消费明细
  for (const usage of usages) {
    await c.env.DB.prepare(
      'INSERT INTO order_points_usage (id, order_id, ledger_id, amount) VALUES (?, ?, ?, ?)'
    ).bind(generateId(), orderId, usage.ledger_id, usage.amount).run()
  }

  // 记录积分事件
  await c.env.DB.prepare(
    `INSERT INTO points_events (id, user_id, manager_id, event_type, amount, related_order_id, description, created_at)
     VALUES (?, ?, ?, 'redeem', ?, ?, ?, ?)`
  ).bind(
    generateId(), userId, user.manager_id,
    -prize.points_required, orderId,
    `兑换「${prize.name}」消耗${prize.points_required}积分`,
    now
  ).run()

  return c.json({
    success: true,
    data: { order_id: orderId, verify_code: verifyCode },
  })
})

export default prizes
