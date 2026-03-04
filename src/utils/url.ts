import type { Context } from 'hono'

// 根据请求 URL 和 R2 key 构建完整的文件访问地址
export function fileUrl(c: Context, key: string): string {
  const url = new URL(c.req.url)
  return `${url.origin}/api/files/${key}`
}

// 将数据库中存储的相对路径（如 /api/files/xxx）转为绝对 URL
export function resolveFileUrl(c: Context, path: string | null): string | null {
  if (!path) return null
  if (path.startsWith('http')) return path
  const url = new URL(c.req.url)
  return `${url.origin}${path}`
}
