import type { Context, Next } from 'hono'
import type { Env } from '../types'

type RateLimitBinding = 'AUTH_LIMITER' | 'CHECKIN_LIMITER' | 'GENERAL_LIMITER'

/**
 * 基于 Cloudflare 官方 Rate Limiting API 的中间件工厂
 * 文档: https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit
 */
export function rateLimit(binding: RateLimitBinding) {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    // 未登录接口用 IP 做 key，已登录接口后续可改用 userId
    const ip = c.req.header('cf-connecting-ip') || 'unknown'
    const path = new URL(c.req.url).pathname
    const key = `${ip}:${path}`

    const limiter = c.env[binding]
    const { success } = await limiter.limit({ key })

    if (!success) {
      return c.json(
        { success: false, error: '请求过于频繁，请稍后再试' },
        { status: 429 }
      )
    }

    await next()
  }
}

// 预定义中间件：认证接口 10次/分钟
export const authRateLimit = rateLimit('AUTH_LIMITER')

// 预定义中间件：打卡接口 5次/分钟
export const checkinRateLimit = rateLimit('CHECKIN_LIMITER')

// 预定义中间件：通用接口 60次/分钟
export const generalRateLimit = rateLimit('GENERAL_LIMITER')
