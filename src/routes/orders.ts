import { Hono } from 'hono'
import type { Env, ContextVariables } from '../types'
import { authMiddleware, requireRole } from '../middleware/auth'
import { AppError } from '../middleware/error-handler'
import { generateId } from '../utils/crypto'
import { fileUrl } from '../utils/url'

const orders = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

orders.use('*', authMiddleware)

// 打卡者：订单列表（支持状态过滤）
orders.get('/mine', async (c) => {
  const userId = c.get('userId')
  const status = c.req.query('status')
  const page = parseInt(c.req.query('page') || '1')
  const limit = parseInt(c.req.query('limit') || '20')
  const offset = (page - 1) * limit

  let query: string
  let params: any[]

  if (status && ['pending', 'verified', 'cancelled'].includes(status)) {
    query = `SELECT o.*, p.name as prize_name, p.image_key as prize_image_key
             FROM orders o JOIN prizes p ON o.prize_id = p.id
             WHERE o.user_id = ? AND o.status = ?
             ORDER BY o.created_at DESC LIMIT ? OFFSET ?`
    params = [userId, status, limit, offset]
  } else {
    query = `SELECT o.*, p.name as prize_name, p.image_key as prize_image_key
             FROM orders o JOIN prizes p ON o.prize_id = p.id
             WHERE o.user_id = ?
             ORDER BY o.created_at DESC LIMIT ? OFFSET ?`
    params = [userId, limit, offset]
  }

  const { results } = await c.env.DB.prepare(query).bind(...params).all()

  const data = results.map((r: any) => ({
    ...r,
    prize_image_url: r.prize_image_key ? fileUrl(c, r.prize_image_key) : null,
  }))

  return c.json({ success: true, data })
})

// 取消兑换 - 预检（检查是否有过期积分）
orders.get('/cancel-check/:id', async (c) => {
  const userId = c.get('userId')
  const orderId = c.req.param('id')

  const order = await c.env.DB.prepare(
    'SELECT * FROM orders WHERE id = ? AND user_id = ? AND status = ?'
  ).bind(orderId, userId, 'pending').first<any>()

  if (!order) {
    throw new AppError(404, '订单不存在或不可取消')
  }

  const now = Date.now()

  // 检查订单消费的积分中有多少已过期
  const { results: usages } = await c.env.DB.prepare(
    `SELECT opu.amount, pl.expires_at
     FROM order_points_usage opu
     JOIN points_ledger pl ON opu.ledger_id = pl.id
     WHERE opu.order_id = ?`
  ).bind(orderId).all<{ amount: number; expires_at: number }>()

  let expiredAmount = 0
  for (const usage of usages) {
    if (usage.expires_at <= now) {
      expiredAmount += usage.amount
    }
  }

  return c.json({
    success: true,
    data: {
      order_id: orderId,
      points_spent: order.points_spent,
      expired_points: expiredAmount,
      refundable_points: order.points_spent - expiredAmount,
      has_expired: expiredAmount > 0,
    },
  })
})

// 取消兑换 - 执行
orders.post('/cancel/:id', async (c) => {
  const userId = c.get('userId')
  const orderId = c.req.param('id')

  const order = await c.env.DB.prepare(
    'SELECT * FROM orders WHERE id = ? AND user_id = ? AND status = ?'
  ).bind(orderId, userId, 'pending').first<any>()

  if (!order) {
    throw new AppError(404, '订单不存在或不可取消')
  }

  const now = Date.now()

  // 还原积分到 ledger
  const { results: usages } = await c.env.DB.prepare(
    'SELECT ledger_id, amount FROM order_points_usage WHERE order_id = ?'
  ).bind(orderId).all<{ ledger_id: string; amount: number }>()

  let restoredTotal = 0
  let expiredTotal = 0

  for (const usage of usages) {
    const ledger = await c.env.DB.prepare(
      'SELECT expires_at FROM points_ledger WHERE id = ?'
    ).bind(usage.ledger_id).first<{ expires_at: number }>()

    if (ledger && ledger.expires_at > now) {
      // 未过期，还原
      await c.env.DB.prepare(
        'UPDATE points_ledger SET remaining_amount = remaining_amount + ? WHERE id = ?'
      ).bind(usage.amount, usage.ledger_id).run()
      restoredTotal += usage.amount
    } else {
      expiredTotal += usage.amount
    }
  }

  // 更新订单状态
  await c.env.DB.prepare(
    'UPDATE orders SET status = ?, cancelled_at = ? WHERE id = ?'
  ).bind('cancelled', now, orderId).run()

  // 还原库存
  await c.env.DB.prepare(
    'UPDATE prizes SET stock = stock + 1, updated_at = ? WHERE id = ?'
  ).bind(now, order.prize_id).run()

  // 记录积分事件
  if (restoredTotal > 0) {
    await c.env.DB.prepare(
      `INSERT INTO points_events (id, user_id, manager_id, event_type, amount, related_order_id, description, created_at)
       VALUES (?, ?, ?, 'cancel_redeem', ?, ?, ?, ?)`
    ).bind(
      generateId(), userId, order.manager_id,
      restoredTotal, orderId,
      `取消兑换，还原${restoredTotal}积分${expiredTotal > 0 ? `（${expiredTotal}积分已过期）` : ''}`,
      now
    ).run()
  }

  return c.json({
    success: true,
    data: { restored_points: restoredTotal, expired_points: expiredTotal },
  })
})

// 管理者：核销订单
orders.post('/verify', requireRole('manager'), async (c) => {
  const managerId = c.get('userId')
  const body = await c.req.json<{ verify_code: string }>()

  if (!body.verify_code) {
    throw new AppError(400, '请提供核销码')
  }

  const order = await c.env.DB.prepare(
    'SELECT * FROM orders WHERE verify_code = ? AND manager_id = ? AND status = ?'
  ).bind(body.verify_code, managerId, 'pending').first<any>()

  if (!order) {
    throw new AppError(404, '核销码无效或订单已处理')
  }

  await c.env.DB.prepare(
    'UPDATE orders SET status = ?, verified_at = ? WHERE id = ?'
  ).bind('verified', Date.now(), order.id).run()

  // 获取奖品信息用于返回
  const prize = await c.env.DB.prepare(
    'SELECT name FROM prizes WHERE id = ?'
  ).bind(order.prize_id).first<{ name: string }>()

  const user = await c.env.DB.prepare(
    'SELECT nickname FROM users WHERE id = ?'
  ).bind(order.user_id).first<{ nickname: string }>()

  return c.json({
    success: true,
    data: {
      order_id: order.id,
      prize_name: prize?.name,
      user_nickname: user?.nickname,
    },
  })
})

// 管理者：订单列表
orders.get('/manage', requireRole('manager'), async (c) => {
  const managerId = c.get('userId')
  const status = c.req.query('status')
  const page = parseInt(c.req.query('page') || '1')
  const limit = parseInt(c.req.query('limit') || '20')
  const offset = (page - 1) * limit

  let query: string
  let params: any[]

  if (status && ['pending', 'verified', 'cancelled'].includes(status)) {
    query = `SELECT o.*, p.name as prize_name, u.nickname as user_nickname
             FROM orders o
             JOIN prizes p ON o.prize_id = p.id
             JOIN users u ON o.user_id = u.id
             WHERE o.manager_id = ? AND o.status = ?
             ORDER BY o.created_at DESC LIMIT ? OFFSET ?`
    params = [managerId, status, limit, offset]
  } else {
    query = `SELECT o.*, p.name as prize_name, u.nickname as user_nickname
             FROM orders o
             JOIN prizes p ON o.prize_id = p.id
             JOIN users u ON o.user_id = u.id
             WHERE o.manager_id = ?
             ORDER BY o.created_at DESC LIMIT ? OFFSET ?`
    params = [managerId, limit, offset]
  }

  const { results } = await c.env.DB.prepare(query).bind(...params).all()

  return c.json({ success: true, data: results })
})

export default orders
