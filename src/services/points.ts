import type { Env, ManagerConfigRow, PointsLedgerRow } from '../types'
import { generateId } from '../utils/crypto'
import { getLogicalDate, calculateConsecutiveDays } from '../utils/date'

/**
 * 打卡后积分结算：
 * 1. 检查连续打卡是否达到周期天数 -> 发放积分
 * 2. 检查是否达到额外奖励条件 -> 发放额外积分
 * 3. 检查是否触发惩罚（长期未打卡）
 */
export async function settlePointsAfterCheckin(
  env: Env,
  userId: string,
  managerId: string,
  config: ManagerConfigRow
) {
  const now = Date.now()
  const today = getLogicalDate(now, config.day_reset_hour)

  // 获取打卡记录计算连续天数
  const { results: recentCheckins } = await env.DB.prepare(
    'SELECT checkin_date FROM checkins WHERE user_id = ? AND manager_id = ? ORDER BY checkin_date DESC LIMIT 500'
  ).bind(userId, managerId).all<{ checkin_date: string }>()

  const dates = recentCheckins.map((r) => r.checkin_date)
  const consecutiveDays = calculateConsecutiveDays(dates, today)

  // 1. 周期积分：连续打卡天数刚好是 checkin_days 的整数倍
  if (consecutiveDays > 0 && consecutiveDays % config.checkin_days === 0) {
    const expiresAt = now + config.points_expiry_days * 24 * 60 * 60 * 1000
    const ledgerId = generateId()

    await env.DB.prepare(
      `INSERT INTO points_ledger (id, user_id, manager_id, source_type, source_id, original_amount, remaining_amount, earned_at, expires_at)
       VALUES (?, ?, ?, 'checkin_reward', NULL, ?, ?, ?, ?)`
    ).bind(ledgerId, userId, managerId, config.points_per_cycle, config.points_per_cycle, now, expiresAt).run()

    await env.DB.prepare(
      `INSERT INTO points_events (id, user_id, manager_id, event_type, amount, related_order_id, description, created_at)
       VALUES (?, ?, ?, 'earn_checkin', ?, NULL, ?, ?)`
    ).bind(
      generateId(), userId, managerId,
      config.points_per_cycle,
      `连续打卡${consecutiveDays}天，获得${config.points_per_cycle}积分`,
      now
    ).run()
  }

  // 2. 额外奖励：完成的周期数是 bonus_cycles 的整数倍
  const completedCycles = Math.floor(consecutiveDays / config.checkin_days)
  if (
    completedCycles > 0 &&
    completedCycles % config.bonus_cycles === 0 &&
    consecutiveDays % config.checkin_days === 0 // 确保刚好在周期结束时触发
  ) {
    const expiresAt = now + config.points_expiry_days * 24 * 60 * 60 * 1000
    const ledgerId = generateId()

    await env.DB.prepare(
      `INSERT INTO points_ledger (id, user_id, manager_id, source_type, source_id, original_amount, remaining_amount, earned_at, expires_at)
       VALUES (?, ?, ?, 'bonus_reward', NULL, ?, ?, ?, ?)`
    ).bind(ledgerId, userId, managerId, config.bonus_points, config.bonus_points, now, expiresAt).run()

    await env.DB.prepare(
      `INSERT INTO points_events (id, user_id, manager_id, event_type, amount, related_order_id, description, created_at)
       VALUES (?, ?, ?, 'earn_bonus', ?, NULL, ?, ?)`
    ).bind(
      generateId(), userId, managerId,
      config.bonus_points,
      `连续${completedCycles}次全勤，额外奖励${config.bonus_points}积分`,
      now
    ).run()
  }
}

/**
 * 检查并执行惩罚（打卡时调用）
 * 连续 N 天未打卡扣减积分
 */
export async function checkAndApplyPenalty(
  env: Env,
  userId: string,
  managerId: string,
  config: ManagerConfigRow
) {
  const now = Date.now()
  const today = getLogicalDate(now, config.day_reset_hour)

  // 获取最近一次打卡（排除今天）
  const lastCheckin = await env.DB.prepare(
    `SELECT checkin_date FROM checkins WHERE user_id = ? AND manager_id = ? AND checkin_date < ? ORDER BY checkin_date DESC LIMIT 1`
  ).bind(userId, managerId, today).first<{ checkin_date: string }>()

  if (!lastCheckin) return

  const lastDate = new Date(lastCheckin.checkin_date)
  const todayDate = new Date(today)
  const gapDays = Math.floor((todayDate.getTime() - lastDate.getTime()) / (24 * 60 * 60 * 1000))

  if (gapDays >= config.penalty_inactive_days) {
    // 计算需要扣减的积分（每超过 penalty_inactive_days 扣一次）
    const penaltyCount = Math.floor(gapDays / config.penalty_inactive_days)
    const totalPenalty = penaltyCount * config.penalty_points

    // FIFO 扣减积分（从最快过期的开始）
    await deductPoints(env, userId, managerId, totalPenalty, 'penalty',
      `连续${gapDays}天未打卡，扣减${totalPenalty}积分`)
  }
}

/**
 * FIFO 扣减积分（惩罚用，最低到0）
 */
async function deductPoints(
  env: Env,
  userId: string,
  managerId: string,
  amount: number,
  eventType: string,
  description: string
) {
  const now = Date.now()

  // 先过期失效的积分
  await expirePoints(env, userId, managerId)

  // 查询可用积分（FIFO: 按过期时间升序）
  const { results: ledgers } = await env.DB.prepare(
    `SELECT id, remaining_amount FROM points_ledger
     WHERE user_id = ? AND manager_id = ? AND remaining_amount > 0 AND expires_at > ?
     ORDER BY expires_at ASC`
  ).bind(userId, managerId, now).all<Pick<PointsLedgerRow, 'id' | 'remaining_amount'>>()

  let remaining = amount
  for (const ledger of ledgers) {
    if (remaining <= 0) break
    const deduct = Math.min(remaining, ledger.remaining_amount)
    await env.DB.prepare(
      'UPDATE points_ledger SET remaining_amount = remaining_amount - ? WHERE id = ?'
    ).bind(deduct, ledger.id).run()
    remaining -= deduct
  }

  // 实际扣减的数量
  const actualDeducted = amount - remaining

  if (actualDeducted > 0) {
    await env.DB.prepare(
      `INSERT INTO points_events (id, user_id, manager_id, event_type, amount, related_order_id, description, created_at)
       VALUES (?, ?, ?, ?, ?, NULL, ?, ?)`
    ).bind(generateId(), userId, managerId, eventType, -actualDeducted, description, now).run()
  }
}

/**
 * Lazy过期处理：将已过期的积分标记为0
 */
export async function expirePoints(env: Env, userId: string, managerId: string) {
  const now = Date.now()

  const { results: expired } = await env.DB.prepare(
    `SELECT id, remaining_amount FROM points_ledger
     WHERE user_id = ? AND manager_id = ? AND remaining_amount > 0 AND expires_at <= ?`
  ).bind(userId, managerId, now).all<Pick<PointsLedgerRow, 'id' | 'remaining_amount'>>()

  for (const ledger of expired) {
    await env.DB.prepare(
      'UPDATE points_ledger SET remaining_amount = 0 WHERE id = ?'
    ).bind(ledger.id).run()

    await env.DB.prepare(
      `INSERT INTO points_events (id, user_id, manager_id, event_type, amount, related_order_id, description, created_at)
       VALUES (?, ?, ?, 'expire', ?, NULL, ?, ?)`
    ).bind(
      generateId(), userId, managerId,
      -ledger.remaining_amount,
      `${ledger.remaining_amount}积分已过期`,
      now
    ).run()
  }
}

/**
 * 查询可用积分余额
 */
export async function getAvailablePoints(env: Env, userId: string, managerId: string): Promise<number> {
  await expirePoints(env, userId, managerId)

  const result = await env.DB.prepare(
    `SELECT COALESCE(SUM(remaining_amount), 0) as total
     FROM points_ledger WHERE user_id = ? AND manager_id = ? AND remaining_amount > 0 AND expires_at > ?`
  ).bind(userId, managerId, Date.now()).first<{ total: number }>()

  return result?.total || 0
}

/**
 * 查询即将过期的积分（30天内）
 */
export async function getExpiringPoints(env: Env, userId: string, managerId: string): Promise<number> {
  const now = Date.now()
  const thirtyDaysLater = now + 30 * 24 * 60 * 60 * 1000

  const result = await env.DB.prepare(
    `SELECT COALESCE(SUM(remaining_amount), 0) as total
     FROM points_ledger WHERE user_id = ? AND manager_id = ? AND remaining_amount > 0
     AND expires_at > ? AND expires_at <= ?`
  ).bind(userId, managerId, now, thirtyDaysLater).first<{ total: number }>()

  return result?.total || 0
}
