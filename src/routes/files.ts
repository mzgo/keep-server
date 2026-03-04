import { Hono } from 'hono'
import type { Env } from '../types'
import { AppError } from '../middleware/error-handler'

const files = new Hono<{ Bindings: Env }>()

// 允许访问的 R2 路径前缀
const ALLOWED_PREFIXES = ['avatars/', 'checkins/', 'prizes/']

files.get('/:path{.+}', async (c) => {
  const path = c.req.param('path')

  // 防止目录遍历
  if (path.includes('..') || path.startsWith('/')) {
    throw new AppError(403, '非法路径')
  }

  // 仅允许访问已知前缀下的文件
  if (!ALLOWED_PREFIXES.some((p) => path.startsWith(p))) {
    throw new AppError(403, '非法路径')
  }

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
