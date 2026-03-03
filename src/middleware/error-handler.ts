import type { Context, Next } from 'hono'
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

export async function errorHandler(c: Context<{ Bindings: Env }>, next: Next) {
  try {
    await next()
  } catch (err) {
    if (err instanceof AppError) {
      return c.json(
        { success: false, error: err.message, code: err.code },
        err.statusCode as any
      )
    }

    console.error('Unhandled error:', err)
    return c.json(
      { success: false, error: '服务器内部错误' },
      500
    )
  }
}
