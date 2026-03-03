import { Hono } from 'hono'
import type { Env, ContextVariables, ManagerConfigRow } from '../types'
import { authMiddleware } from '../middleware/auth'
import { AppError } from '../middleware/error-handler'
import { generateId } from '../utils/crypto'
import { getLogicalDate, calculateConsecutiveDays } from '../utils/date'
import { settlePointsAfterCheckin, checkAndApplyPenalty } from '../services/points'

const checkin = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

checkin.use('*', authMiddleware)

// 打卡
checkin.post('/', async (c) => {
  const userId = c.get('userId')

  // 获取用户信息和管理者ID
  const user = await c.env.DB.prepare(
    'SELECT manager_id, role FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null; role: string }>()

  if (!user || user.role !== 'checker') {
    throw new AppError(403, '仅打卡者可以打卡')
  }

  if (!user.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  // 获取管理者配置
  const config = await c.env.DB.prepare(
    'SELECT * FROM manager_configs WHERE manager_id = ?'
  ).bind(user.manager_id).first<ManagerConfigRow>()

  if (!config) {
    throw new AppError(400, '管理者未完成配置')
  }

  const now = Date.now()
  const today = getLogicalDate(now, config.day_reset_hour)

  // 检查今日是否已打卡
  const existing = await c.env.DB.prepare(
    'SELECT id FROM checkins WHERE user_id = ? AND manager_id = ? AND checkin_date = ?'
  ).bind(userId, user.manager_id, today).first()

  if (existing) {
    throw new AppError(400, '今日已打卡')
  }

  // 处理图片上传
  const formData = await c.req.formData()
  const imageFile = formData.get('image') as File | null
  const note = formData.get('note') as string | null

  if (!imageFile) {
    throw new AppError(400, '请上传打卡照片')
  }

  // 存储图片到 R2
  const ext = imageFile.name.split('.').pop() || 'jpg'
  const imageKey = `checkins/${userId}/${today}.${ext}`

  await c.env.R2.put(imageKey, imageFile.stream(), {
    httpMetadata: { contentType: imageFile.type },
  })

  // 创建打卡记录
  const checkinId = generateId()
  await c.env.DB.prepare(
    `INSERT INTO checkins (id, user_id, manager_id, image_key, note, checkin_date, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).bind(checkinId, userId, user.manager_id, imageKey, note || null, today, now).run()

  // 惩罚检查（在积分结算之前）
  await checkAndApplyPenalty(c.env, userId, user.manager_id, config)

  // 积分结算
  await settlePointsAfterCheckin(c.env, userId, user.manager_id, config)

  return c.json({
    success: true,
    data: {
      id: checkinId,
      checkin_date: today,
      image_url: `/api/files/${imageKey}`,
    },
  })
})

// 获取打卡状态
checkin.get('/status', async (c) => {
  const userId = c.get('userId')

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const config = await c.env.DB.prepare(
    'SELECT * FROM manager_configs WHERE manager_id = ?'
  ).bind(user.manager_id).first<ManagerConfigRow>()

  if (!config) {
    throw new AppError(400, '管理者未完成配置')
  }

  const now = Date.now()
  const today = getLogicalDate(now, config.day_reset_hour)

  // 今日是否已打卡
  const todayCheckin = await c.env.DB.prepare(
    'SELECT id FROM checkins WHERE user_id = ? AND manager_id = ? AND checkin_date = ?'
  ).bind(userId, user.manager_id, today).first()

  // 获取最近的打卡记录来计算连续天数
  const { results: recentCheckins } = await c.env.DB.prepare(
    'SELECT checkin_date FROM checkins WHERE user_id = ? AND manager_id = ? ORDER BY checkin_date DESC LIMIT 100'
  ).bind(userId, user.manager_id).all<{ checkin_date: string }>()

  const dates = recentCheckins.map((r) => r.checkin_date)
  const consecutiveDays = calculateConsecutiveDays(dates, today)

  // 当前周期进度
  const cycleProgress = consecutiveDays % config.checkin_days
  const daysUntilReward = config.checkin_days - cycleProgress

  // 全勤周期数（额外奖励进度）
  const completedCycles = Math.floor(consecutiveDays / config.checkin_days)
  const bonusProgress = completedCycles % config.bonus_cycles

  return c.json({
    success: true,
    data: {
      checked_today: !!todayCheckin,
      today_date: today,
      consecutive_days: consecutiveDays,
      cycle_progress: cycleProgress,
      days_until_reward: cycleProgress === 0 && consecutiveDays > 0 ? 0 : daysUntilReward,
      checkin_days: config.checkin_days,
      points_per_cycle: config.points_per_cycle,
      completed_cycles: completedCycles,
      bonus_progress: bonusProgress,
      bonus_cycles: config.bonus_cycles,
      bonus_points: config.bonus_points,
    },
  })
})

// 获取打卡历史（分页）
checkin.get('/history', async (c) => {
  const userId = c.get('userId')
  const page = parseInt(c.req.query('page') || '1')
  const limit = parseInt(c.req.query('limit') || '30')
  const offset = (page - 1) * limit

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const { results } = await c.env.DB.prepare(
    `SELECT id, image_key, note, checkin_date, created_at
     FROM checkins WHERE user_id = ? AND manager_id = ?
     ORDER BY checkin_date DESC LIMIT ? OFFSET ?`
  ).bind(userId, user.manager_id, limit, offset).all()

  // 补充图片URL
  const data = results.map((r: any) => ({
    ...r,
    image_url: `/api/files/${r.image_key}`,
  }))

  return c.json({ success: true, data })
})

// 获取指定月份的打卡日期（日历用）
checkin.get('/calendar', async (c) => {
  const userId = c.get('userId')
  const year = c.req.query('year')
  const month = c.req.query('month')

  if (!year || !month) {
    throw new AppError(400, '缺少 year/month 参数')
  }

  const user = await c.env.DB.prepare(
    'SELECT manager_id FROM users WHERE id = ?'
  ).bind(userId).first<{ manager_id: string | null }>()

  if (!user?.manager_id) {
    throw new AppError(400, '未绑定管理者')
  }

  const startDate = `${year}-${month.padStart(2, '0')}-01`
  const endDate = `${year}-${month.padStart(2, '0')}-31`

  const { results } = await c.env.DB.prepare(
    `SELECT checkin_date, image_key FROM checkins
     WHERE user_id = ? AND manager_id = ? AND checkin_date >= ? AND checkin_date <= ?
     ORDER BY checkin_date ASC`
  ).bind(userId, user.manager_id, startDate, endDate).all()

  const data = results.map((r: any) => ({
    date: r.checkin_date,
    image_url: `/api/files/${r.image_key}`,
  }))

  return c.json({ success: true, data })
})

// 管理者查看打卡者记录
checkin.get('/manager/records', async (c) => {
  const managerId = c.get('userId')
  const userRole = c.get('userRole')

  if (userRole !== 'manager') {
    throw new AppError(403, '无权访问')
  }

  const checkerId = c.req.query('checker_id')
  const page = parseInt(c.req.query('page') || '1')
  const limit = parseInt(c.req.query('limit') || '30')
  const offset = (page - 1) * limit

  let query: string
  let params: any[]

  if (checkerId) {
    query = `SELECT c.id, c.user_id, c.image_key, c.note, c.checkin_date, c.created_at, u.nickname
             FROM checkins c JOIN users u ON c.user_id = u.id
             WHERE c.manager_id = ? AND c.user_id = ?
             ORDER BY c.checkin_date DESC LIMIT ? OFFSET ?`
    params = [managerId, checkerId, limit, offset]
  } else {
    query = `SELECT c.id, c.user_id, c.image_key, c.note, c.checkin_date, c.created_at, u.nickname
             FROM checkins c JOIN users u ON c.user_id = u.id
             WHERE c.manager_id = ?
             ORDER BY c.checkin_date DESC LIMIT ? OFFSET ?`
    params = [managerId, limit, offset]
  }

  const { results } = await c.env.DB.prepare(query).bind(...params).all()

  const data = results.map((r: any) => ({
    ...r,
    image_url: `/api/files/${r.image_key}`,
  }))

  return c.json({ success: true, data })
})

export default checkin
