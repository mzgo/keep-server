import type { Context, Next } from 'hono'
import type { Env, ContextVariables } from '../types'
import { verifyJwt } from '../utils/jwt'
import { AppError } from './error-handler'

export async function authMiddleware(
  c: Context<{ Bindings: Env; Variables: ContextVariables }>,
  next: Next
) {
  const authHeader = c.req.header('Authorization')
  if (!authHeader?.startsWith('Bearer ')) {
    throw new AppError(401, '未登录或登录已过期')
  }

  const token = authHeader.slice(7)
  const payload = await verifyJwt(token, c.env.JWT_SECRET)

  if (!payload) {
    throw new AppError(401, '登录已过期，请重新登录')
  }

  c.set('userId', payload.sub)
  c.set('userRole', payload.role)
  c.set('username', payload.username)

  await next()
}

// 角色检查中间件工厂
export function requireRole(role: 'manager' | 'checker') {
  return async (c: Context<{ Bindings: Env; Variables: ContextVariables }>, next: Next) => {
    if (c.get('userRole') !== role) {
      throw new AppError(403, '无权访问')
    }
    await next()
  }
}
