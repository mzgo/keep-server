import type { Context, Next } from 'hono'
import type { Env } from '../types'

function isAllowedOrigin(origin: string, frontendUrl: string): boolean {
  if (origin === frontendUrl) return true
  if (origin.startsWith('http://localhost:')) return true
  return false
}

export function createCorsMiddleware() {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    const frontendUrl = c.env.FRONTEND_URL
    const origin = c.req.header('Origin') || ''
    const allowedOrigin = isAllowedOrigin(origin, frontendUrl) ? origin : frontendUrl

    // 预检请求直接返回
    if (c.req.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': allowedOrigin,
          'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
          'Access-Control-Expose-Headers': 'Content-Length',
          'Access-Control-Max-Age': '86400',
          'Access-Control-Allow-Credentials': 'true',
        },
      })
    }

    await next()

    // Workers 运行时中 Response.headers 不可变，需要创建新 Response 来附加 CORS 头
    const res = c.res
    const headers = new Headers(res.headers)
    headers.set('Access-Control-Allow-Origin', allowedOrigin)
    headers.set('Access-Control-Allow-Credentials', 'true')
    headers.set('Access-Control-Expose-Headers', 'Content-Length')
    c.res = new Response(res.body, { status: res.status, statusText: res.statusText, headers })
  }
}
