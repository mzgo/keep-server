import { Hono } from 'hono'
import type { Env, ContextVariables, ManagerConfigRow } from '../types'
import { authMiddleware, requireRole } from '../middleware/auth'
import { AppError } from '../middleware/error-handler'
import { generateId, generateToken } from '../utils/crypto'

const manager = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

manager.use('*', authMiddleware)
manager.use('*', requireRole('manager'))

// 获取管理者配置
manager.get('/config', async (c) => {
  const managerId = c.get('userId')
  const config = await c.env.DB.prepare(
    'SELECT * FROM manager_configs WHERE manager_id = ?'
  ).bind(managerId).first<ManagerConfigRow>()

  if (!config) {
    throw new AppError(404, '配置不存在')
  }

  return c.json({ success: true, data: config })
})

// 更新管理者配置
manager.put('/config', async (c) => {
  const managerId = c.get('userId')
  const body = await c.req.json<{
    checkin_days?: number
    points_per_cycle?: number
    bonus_cycles?: number
    bonus_points?: number
    points_expiry_days?: number
    penalty_inactive_days?: number
    penalty_points?: number
    day_reset_hour?: number
  }>()

  const updates: string[] = []
  const values: any[] = []

  const fields = [
    'checkin_days', 'points_per_cycle', 'bonus_cycles', 'bonus_points',
    'points_expiry_days', 'penalty_inactive_days', 'penalty_points', 'day_reset_hour',
  ] as const

  for (const field of fields) {
    if (body[field] !== undefined) {
      const minValue = field === 'day_reset_hour' ? 0 : 1
      if (typeof body[field] !== 'number' || body[field]! < minValue) {
        throw new AppError(400, field === 'day_reset_hour'
          ? `${field} 必须是非负整数`
          : `${field} 必须是正整数`)
      }
      updates.push(`${field} = ?`)
      values.push(body[field])
    }
  }

  if (updates.length === 0) {
    throw new AppError(400, '没有要更新的字段')
  }

  // 标记为已配置
  updates.push('is_configured = 1')
  updates.push('updated_at = ?')
  values.push(Date.now())
  values.push(managerId)

  await c.env.DB.prepare(
    `UPDATE manager_configs SET ${updates.join(', ')} WHERE manager_id = ?`
  ).bind(...values).run()

  return c.json({ success: true, message: '配置已更新' })
})

// 检查配置状态
manager.get('/config/status', async (c) => {
  const managerId = c.get('userId')
  const config = await c.env.DB.prepare(
    'SELECT is_configured FROM manager_configs WHERE manager_id = ?'
  ).bind(managerId).first<{ is_configured: number }>()

  return c.json({
    success: true,
    data: { is_configured: config?.is_configured === 1 },
  })
})

// 创建邀请链接
manager.post('/invitations', async (c) => {
  const managerId = c.get('userId')

  // 检查是否已完成配置
  const config = await c.env.DB.prepare(
    'SELECT is_configured FROM manager_configs WHERE manager_id = ?'
  ).bind(managerId).first<{ is_configured: number }>()

  if (!config || config.is_configured !== 1) {
    throw new AppError(400, '请先完成基础配置')
  }

  const code = generateToken().slice(0, 16)
  const id = generateId()
  const now = Date.now()

  await c.env.DB.prepare(
    'INSERT INTO invitations (id, manager_id, code, is_used, created_at) VALUES (?, ?, ?, 0, ?)'
  ).bind(id, managerId, code, now).run()

  return c.json({
    success: true,
    data: { code, id },
  })
})

// 获取邀请列表
manager.get('/invitations', async (c) => {
  const managerId = c.get('userId')
  const { results } = await c.env.DB.prepare(
    'SELECT * FROM invitations WHERE manager_id = ? ORDER BY created_at DESC'
  ).bind(managerId).all()

  return c.json({ success: true, data: results })
})

// 获取绑定的打卡者列表
manager.get('/checkers', async (c) => {
  const managerId = c.get('userId')
  const { results } = await c.env.DB.prepare(
    'SELECT id, username, nickname, avatar_url, created_at FROM users WHERE manager_id = ? AND role = ?'
  ).bind(managerId, 'checker').all()

  return c.json({ success: true, data: results })
})

export default manager
