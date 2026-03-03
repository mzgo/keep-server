import { Hono } from 'hono'
import type { Env } from './types'
import { createCorsMiddleware } from './middleware/cors'
import { errorHandler } from './middleware/error-handler'
import api from './routes'

const app = new Hono<{ Bindings: Env }>()

// 全局中间件
app.use('*', createCorsMiddleware())
app.use('*', errorHandler)

// API 路由
app.route('/api', api)

// 根路径
app.get('/', (c) => {
  return c.json({
    name: '脂付宝 API',
    version: '0.1.0',
  })
})

export default app
