import { Hono } from 'hono'
import type { Env, ContextVariables } from '../types'
import { authRateLimit, checkinRateLimit, generalRateLimit } from '../middleware/rate-limit'
import health from './health'
import auth from './auth'
import files from './files'
import manager from './manager'
import checkin from './checkin'
import points from './points'
import prizes from './prizes'
import orders from './orders'

const api = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

// 按模块应用不同的限流策略
api.use('/auth/*', authRateLimit)
api.use('/checkin/*', checkinRateLimit)
api.use('/manager/*', generalRateLimit)
api.use('/points/*', generalRateLimit)
api.use('/prizes/*', generalRateLimit)
api.use('/orders/*', generalRateLimit)

api.route('/', health)
api.route('/auth', auth)
api.route('/files', files)
api.route('/manager', manager)
api.route('/checkin', checkin)
api.route('/points', points)
api.route('/prizes', prizes)
api.route('/orders', orders)

export default api
