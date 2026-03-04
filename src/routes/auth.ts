import { Hono } from 'hono'
import type { Env, ContextVariables, UserRow } from '../types'
import { generateCaptcha } from '../utils/captcha'
import { generateId, generateSalt, generateToken, hashPassword, verifyPassword } from '../utils/crypto'
import { signJwt } from '../utils/jwt'
import { AppError } from '../middleware/error-handler'
import { authMiddleware } from '../middleware/auth'
import { fileUrl, resolveFileUrl } from '../utils/url'

const auth = new Hono<{ Bindings: Env; Variables: ContextVariables }>()

// 获取验证码
auth.get('/captcha', async (c) => {
  const { text, svg } = generateCaptcha()
  const captchaId = generateId()

  // 存入 KV，5分钟过期
  await c.env.KV.put(`captcha:${captchaId}`, text, { expirationTtl: 300 })

  return c.json({
    success: true,
    data: { captcha_id: captchaId, svg },
  })
})

// 注册
auth.post('/register', async (c) => {
  const body = await c.req.json<{
    username: string
    password: string
    captcha_id: string
    captcha_text: string
    email?: string
    role: 'manager' | 'checker'
    invite_code?: string
  }>()

  // 校验必填字段
  if (!body.username || !body.password || !body.captcha_id || !body.captcha_text || !body.role) {
    throw new AppError(400, '缺少必填字段')
  }

  if (body.username.length < 3 || body.username.length > 20) {
    throw new AppError(400, '用户名长度需在3-20个字符之间')
  }

  if (body.password.length < 6) {
    throw new AppError(400, '密码长度至少6个字符')
  }

  // 校验验证码
  const storedCaptcha = await c.env.KV.get(`captcha:${body.captcha_id}`)
  if (!storedCaptcha || storedCaptcha !== body.captcha_text.toLowerCase()) {
    throw new AppError(400, '验证码错误')
  }
  await c.env.KV.delete(`captcha:${body.captcha_id}`)

  // 检查用户名唯一性
  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE username = ?')
    .bind(body.username)
    .first()
  if (existing) {
    throw new AppError(409, '用户名已存在')
  }

  const userId = generateId()
  const salt = generateSalt()
  const passwordHash = await hashPassword(body.password, salt)
  const now = Date.now()

  let managerId: string | null = null
  let invitationId: string | null = null

  // 打卡者必须通过邀请码注册
  if (body.role === 'checker') {
    if (!body.invite_code) {
      throw new AppError(400, '打卡者注册需要邀请码')
    }

    const invitation = await c.env.DB.prepare(
      'SELECT id, manager_id, is_used FROM invitations WHERE code = ?'
    ).bind(body.invite_code).first<{ id: string; manager_id: string; is_used: number }>()

    if (!invitation) {
      throw new AppError(400, '邀请码无效')
    }
    if (invitation.is_used) {
      throw new AppError(400, '邀请码已被使用')
    }

    managerId = invitation.manager_id
    invitationId = invitation.id
  }

  // 先创建用户（必须在更新邀请码之前，否则 invitations.used_by 外键约束会失败）
  await c.env.DB.prepare(
    `INSERT INTO users (id, username, password_hash, salt, email, nickname, avatar_url, role, manager_id, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)`
  ).bind(userId, body.username, passwordHash, salt, body.email || null, body.username, body.role, managerId, now, now).run()

  // 标记邀请码为已使用（用户已插入，外键约束满足）
  if (invitationId) {
    await c.env.DB.prepare(
      'UPDATE invitations SET is_used = 1, used_by = ?, used_at = ? WHERE id = ?'
    ).bind(userId, now, invitationId).run()
  }

  // 管理者自动创建默认配置
  if (body.role === 'manager') {
    await c.env.DB.prepare(
      `INSERT INTO manager_configs (id, manager_id, is_configured, created_at, updated_at)
       VALUES (?, ?, 0, ?, ?)`
    ).bind(generateId(), userId, now, now).run()
  }

  // 签发 Token
  const accessToken = await signJwt(
    { sub: userId, username: body.username, role: body.role },
    c.env.JWT_SECRET,
    15 * 60 * 1000
  )
  const refreshToken = generateToken()
  await c.env.KV.put(`refresh_token:${refreshToken}`, userId, { expirationTtl: 90 * 24 * 60 * 60 })

  return c.json({
    success: true,
    data: {
      access_token: accessToken,
      refresh_token: refreshToken,
      user: {
        id: userId,
        username: body.username,
        nickname: body.username,
        avatar_url: null,
        role: body.role,
        email: body.email || null,
        manager_id: managerId,
      },
    },
  })
})

// 登录
auth.post('/login', async (c) => {
  const body = await c.req.json<{
    username: string
    password: string
    captcha_id: string
    captcha_text: string
  }>()

  if (!body.username || !body.password || !body.captcha_id || !body.captcha_text) {
    throw new AppError(400, '缺少必填字段')
  }

  // 校验验证码
  const storedCaptcha = await c.env.KV.get(`captcha:${body.captcha_id}`)
  if (!storedCaptcha || storedCaptcha !== body.captcha_text.toLowerCase()) {
    throw new AppError(400, '验证码错误')
  }
  await c.env.KV.delete(`captcha:${body.captcha_id}`)

  // 查找用户
  const user = await c.env.DB.prepare(
    'SELECT id, username, password_hash, salt, email, nickname, avatar_url, role, manager_id FROM users WHERE username = ?'
  ).bind(body.username).first<UserRow>()

  if (!user) {
    throw new AppError(401, '用户名或密码错误')
  }

  // 校验密码
  const isValid = await verifyPassword(body.password, user.salt, user.password_hash)
  if (!isValid) {
    throw new AppError(401, '用户名或密码错误')
  }

  // 签发 Token
  const accessToken = await signJwt(
    { sub: user.id, username: user.username, role: user.role },
    c.env.JWT_SECRET,
    15 * 60 * 1000
  )
  const refreshToken = generateToken()
  await c.env.KV.put(`refresh_token:${refreshToken}`, user.id, { expirationTtl: 90 * 24 * 60 * 60 })

  return c.json({
    success: true,
    data: {
      access_token: accessToken,
      refresh_token: refreshToken,
      user: {
        id: user.id,
        username: user.username,
        nickname: user.nickname,
        avatar_url: resolveFileUrl(c, user.avatar_url),
        role: user.role,
        email: user.email,
        manager_id: user.manager_id,
      },
    },
  })
})

// 刷新 Token（Token Rotation）
auth.post('/refresh', async (c) => {
  const body = await c.req.json<{ refresh_token: string }>()

  if (!body.refresh_token) {
    throw new AppError(400, '缺少 refresh_token')
  }

  // 验证旧 Refresh Token
  const userId = await c.env.KV.get(`refresh_token:${body.refresh_token}`)
  if (!userId) {
    throw new AppError(401, '登录已过期，请重新登录')
  }

  // 立即作废旧 token（Rotation）
  await c.env.KV.delete(`refresh_token:${body.refresh_token}`)

  // 查询用户信息
  const user = await c.env.DB.prepare(
    'SELECT id, username, role FROM users WHERE id = ?'
  ).bind(userId).first<Pick<UserRow, 'id' | 'username' | 'role'>>()

  if (!user) {
    throw new AppError(401, '用户不存在')
  }

  // 签发新的双 Token
  const accessToken = await signJwt(
    { sub: user.id, username: user.username, role: user.role },
    c.env.JWT_SECRET,
    15 * 60 * 1000
  )
  const newRefreshToken = generateToken()
  await c.env.KV.put(`refresh_token:${newRefreshToken}`, user.id, { expirationTtl: 90 * 24 * 60 * 60 })

  return c.json({
    success: true,
    data: {
      access_token: accessToken,
      refresh_token: newRefreshToken,
    },
  })
})

// 登出
auth.post('/logout', authMiddleware, async (c) => {
  const body = await c.req.json<{ refresh_token: string }>()
  if (body.refresh_token) {
    await c.env.KV.delete(`refresh_token:${body.refresh_token}`)
  }
  return c.json({ success: true, message: '已退出登录' })
})

// 获取当前用户信息
auth.get('/me', authMiddleware, async (c) => {
  const userId = c.get('userId')
  const user = await c.env.DB.prepare(
    'SELECT id, username, email, nickname, avatar_url, role, manager_id, created_at FROM users WHERE id = ?'
  ).bind(userId).first()

  if (!user) {
    throw new AppError(404, '用户不存在')
  }

  const userData = { ...(user as any), avatar_url: resolveFileUrl(c, (user as any).avatar_url) }
  return c.json({ success: true, data: userData })
})

// 修改个人信息
auth.put('/profile', authMiddleware, async (c) => {
  const userId = c.get('userId')
  const body = await c.req.json<{ nickname?: string; email?: string }>()

  const updates: string[] = []
  const values: any[] = []

  if (body.nickname !== undefined) {
    if (body.nickname.length < 1 || body.nickname.length > 20) {
      throw new AppError(400, '昵称长度需在1-20个字符之间')
    }
    updates.push('nickname = ?')
    values.push(body.nickname)
  }

  if (body.email !== undefined) {
    updates.push('email = ?')
    values.push(body.email || null)
  }

  if (updates.length === 0) {
    throw new AppError(400, '没有要更新的字段')
  }

  updates.push('updated_at = ?')
  values.push(Date.now())
  values.push(userId)

  await c.env.DB.prepare(
    `UPDATE users SET ${updates.join(', ')} WHERE id = ?`
  ).bind(...values).run()

  return c.json({ success: true, message: '更新成功' })
})

// 修改密码
auth.put('/password', authMiddleware, async (c) => {
  const userId = c.get('userId')
  const body = await c.req.json<{ old_password: string; new_password: string }>()

  if (!body.old_password || !body.new_password) {
    throw new AppError(400, '缺少必填字段')
  }

  if (body.new_password.length < 6) {
    throw new AppError(400, '新密码长度至少6个字符')
  }

  const user = await c.env.DB.prepare(
    'SELECT password_hash, salt FROM users WHERE id = ?'
  ).bind(userId).first<Pick<UserRow, 'password_hash' | 'salt'>>()

  if (!user) {
    throw new AppError(404, '用户不存在')
  }

  const isValid = await verifyPassword(body.old_password, user.salt, user.password_hash)
  if (!isValid) {
    throw new AppError(400, '原密码错误')
  }

  const newSalt = generateSalt()
  const newHash = await hashPassword(body.new_password, newSalt)

  await c.env.DB.prepare(
    'UPDATE users SET password_hash = ?, salt = ?, updated_at = ? WHERE id = ?'
  ).bind(newHash, newSalt, Date.now(), userId).run()

  return c.json({ success: true, message: '密码修改成功' })
})

// 上传头像
auth.post('/avatar', authMiddleware, async (c) => {
  const userId = c.get('userId')
  const formData = await c.req.formData()
  const file = formData.get('file') as File | null

  if (!file) {
    throw new AppError(400, '请选择头像图片')
  }

  const maxSize = 2 * 1024 * 1024
  if (file.size > maxSize) {
    throw new AppError(400, '头像图片不能超过2MB')
  }

  // 查询当前头像路径，上传新头像后清理旧文件
  const currentUser = await c.env.DB.prepare(
    'SELECT avatar_url FROM users WHERE id = ?'
  ).bind(userId).first<{ avatar_url: string | null }>()
  const oldAvatarKey = currentUser?.avatar_url?.replace('/api/files/', '') || null

  const ext = file.name.split('.').pop() || 'png'
  const key = `avatars/${userId}.${ext}`

  await c.env.R2.put(key, file.stream(), {
    httpMetadata: { contentType: file.type },
  })

  // 清理旧头像文件（key 不同时才删除，避免覆盖同名文件后误删）
  if (oldAvatarKey && oldAvatarKey !== key) {
    await c.env.R2.delete(oldAvatarKey).catch(() => {})
  }

  const avatarPath = `/api/files/${key}`

  await c.env.DB.prepare(
    'UPDATE users SET avatar_url = ?, updated_at = ? WHERE id = ?'
  ).bind(avatarPath, Date.now(), userId).run()

  return c.json({ success: true, data: { avatar_url: fileUrl(c, key) } })
})

export default auth
