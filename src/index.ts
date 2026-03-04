import { Hono } from 'hono'
import type { Env } from './types'
import { createCorsMiddleware } from './middleware/cors'
import { onAppError } from './middleware/error-handler'
import api from './routes'

const app = new Hono<{ Bindings: Env }>()

// 全局错误处理（Hono 内部 compose 会调用 onError，middleware 形式无法拦截）
app.onError(onAppError)

// 全局中间件
app.use('*', createCorsMiddleware())

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
