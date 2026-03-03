import type { Context } from 'hono'

export interface Env {
  DB: D1Database
  KV: KVNamespace
  R2: R2Bucket
  IMAGES: any
  AUTH_LIMITER: RateLimit
  CHECKIN_LIMITER: RateLimit
  GENERAL_LIMITER: RateLimit
  JWT_SECRET: string
  FRONTEND_URL: string
}

export type AppContext = Context<{ Bindings: Env; Variables: ContextVariables }>

export interface ContextVariables {
  userId: string
  userRole: 'manager' | 'checker'
  username: string
}

export interface UserRow {
  id: string
  username: string
  password_hash: string
  salt: string
  email: string | null
  nickname: string
  avatar_url: string | null
  role: 'manager' | 'checker'
  manager_id: string | null
  created_at: number
  updated_at: number
}

export interface ManagerConfigRow {
  id: string
  manager_id: string
  checkin_days: number
  points_per_cycle: number
  bonus_cycles: number
  bonus_points: number
  points_expiry_days: number
  penalty_inactive_days: number
  penalty_points: number
  day_reset_hour: number
  is_configured: number
  created_at: number
  updated_at: number
}

export interface InvitationRow {
  id: string
  manager_id: string
  code: string
  is_used: number
  used_by: string | null
  created_at: number
  used_at: number | null
}

export interface CheckinRow {
  id: string
  user_id: string
  manager_id: string
  image_key: string
  note: string | null
  checkin_date: string
  created_at: number
}

export interface PointsLedgerRow {
  id: string
  user_id: string
  manager_id: string
  source_type: 'checkin_reward' | 'bonus_reward'
  source_id: string | null
  original_amount: number
  remaining_amount: number
  earned_at: number
  expires_at: number
}

export interface PointsEventRow {
  id: string
  user_id: string
  manager_id: string
  event_type: 'earn_checkin' | 'earn_bonus' | 'redeem' | 'cancel_redeem' | 'expire' | 'penalty'
  amount: number
  related_order_id: string | null
  description: string | null
  created_at: number
}

export interface PrizeRow {
  id: string
  manager_id: string
  name: string
  image_key: string | null
  points_required: number
  stock: number
  is_blind_box: number
  is_active: number
  created_at: number
  updated_at: number
}

export interface OrderRow {
  id: string
  user_id: string
  manager_id: string
  prize_id: string
  points_spent: number
  status: 'pending' | 'verified' | 'cancelled'
  verify_code: string
  created_at: number
  verified_at: number | null
  cancelled_at: number | null
}

export interface OrderPointsUsageRow {
  id: string
  order_id: string
  ledger_id: string
  amount: number
}

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  error?: string
  message?: string
}
