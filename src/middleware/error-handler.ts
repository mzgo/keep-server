import type { Context } from 'hono'
import type { Env } from '../types'

export class AppError extends Error {
  constructor(
    public statusCode: number,
    message: string,
    public code?: string
  ) {
    super(message)
    this.name = 'AppError'
  }
}

// Hono 内部 compose 在每层 dispatch 中捕获错误并调用 onError，
// middleware 形式的 try/catch 无法拦截子路由的错误，必须使用 app.onError()
export function onAppError(err: Error, c: Context<{ Bindings: Env }>) {
  if ('statusCode' in err) {
    const appErr = err as AppError
    return c.json(
      { success: false, error: appErr.message, code: appErr.code },
      appErr.statusCode as any
    )
  }

  console.error('Unhandled error:', err)
  return c.json(
    { success: false, error: '服务器内部错误' },
    500
  )
}
