import { Hono } from 'hono'
import type { Env, ContextVariables } from '../types'
import { authMiddleware } from '../middleware/auth'
import { AppError } from '../middleware/error-handler'
import { getAvailablePoints, getExpiringPoints } from '../services/points'

const points = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

points.use('*', authMiddleware)

// 积分概览
points.get('/summary', async (c) => {
  const userId = c.get('userId')

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const [available, expiring] = await Promise.all([
    getAvailablePoints(c.env, userId, user.manager_id),
    getExpiringPoints(c.env, userId, user.manager_id),
  ])

  return c.json({
    success: true,
    data: { available, expiring_in_30_days: expiring },
  })
})

// 积分流水列表
points.get('/events', async (c) => {
  const userId = c.get('userId')
  const page = parseInt(c.req.query('page') || '1')
  const limit = parseInt(c.req.query('limit') || '20')
  const offset = (page - 1) * limit

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const { results } = await c.env.DB.prepare(
    `SELECT id, event_type, amount, description, created_at
     FROM points_events WHERE user_id = ? AND manager_id = ?
     ORDER BY created_at DESC LIMIT ? OFFSET ?`
  ).bind(userId, user.manager_id, limit, offset).all()

  return c.json({ success: true, data: results })
})

export default points
