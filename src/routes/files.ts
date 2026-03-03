import { Hono } from 'hono'
import type { Env } from '../types'
import { AppError } from '../middleware/error-handler'

const files = new Hono<{ Bindings: Env }>()

// 提供 R2 存储的文件访问
files.get('/:path{.+}', async (c) => {
  const path = c.req.param('path')
  const object = await c.env.R2.get(path)

  if (!object) {
    throw new AppError(404, '文件不存在')
  }

  const headers = new Headers()
  headers.set('Content-Type', object.httpMetadata?.contentType || 'application/octet-stream')
  headers.set('Cache-Control', 'public, max-age=31536000, immutable')

  return new Response(object.body, { headers })
})

export default files
