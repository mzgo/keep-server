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

  // 检查 Referer，限制只能从前端站点或本地开发环境访问
  const referer = c.req.header('Referer') || ''
  const frontendUrl = c.env.FRONTEND_URL
  const isAllowedReferer =
    !referer ||
    referer.startsWith(frontendUrl) ||
    referer.startsWith('http://localhost:')
  if (!isAllowedReferer) {
    throw new AppError(403, '访问被拒绝')
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
