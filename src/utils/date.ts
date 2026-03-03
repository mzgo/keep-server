// 日期工具 - 基于凌晨5点分界的"逻辑日"计算
// Workers 运行在 UTC，需要先转为用户本地时间再做逻辑日计算

// 用户时区偏移（小时），UTC+8 = 中国标准时间
const DEFAULT_TIMEZONE_OFFSET = 8

/**
 * 获取给定时间戳对应的"逻辑日期"
 * 例如 day_reset_hour=5 时，凌晨3点仍属于前一天
 * timezoneOffset: 用户时区偏移（小时），默认 UTC+8
 */
export function getLogicalDate(
  timestampMs: number,
  resetHour: number = 5,
  timezoneOffset: number = DEFAULT_TIMEZONE_OFFSET
): string {
  // 先将 UTC 时间戳转为用户本地时间，再减去重置小时
  const localMs = timestampMs + timezoneOffset * 60 * 60 * 1000
  const adjusted = new Date(localMs - resetHour * 60 * 60 * 1000)
  const year = adjusted.getUTCFullYear()
  const month = String(adjusted.getUTCMonth() + 1).padStart(2, '0')
  const day = String(adjusted.getUTCDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 计算连续打卡天数（从最近一天往回数）
 */
export function calculateConsecutiveDays(
  checkinDates: string[],
  today: string
): number {
  if (checkinDates.length === 0) return 0

  const sorted = [...checkinDates].sort().reverse()

  // 最近的打卡必须是今天或昨天，否则连续中断了
  const latestDate = sorted[0]
  const todayDate = new Date(today)
  const latestDateObj = new Date(latestDate)
  const diffDays = Math.floor((todayDate.getTime() - latestDateObj.getTime()) / (24 * 60 * 60 * 1000))

  if (diffDays > 1) return 0

  let consecutive = 1
  for (let i = 1; i < sorted.length; i++) {
    const prev = new Date(sorted[i - 1])
    const curr = new Date(sorted[i])
    const diff = Math.floor((prev.getTime() - curr.getTime()) / (24 * 60 * 60 * 1000))
    if (diff === 1) {
      consecutive++
    } else {
      break
    }
  }

  return consecutive
}

/**
 * 获取前一天的日期字符串
 */
export function getPreviousDate(dateStr: string): string {
  const date = new Date(dateStr)
  date.setDate(date.getDate() - 1)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
