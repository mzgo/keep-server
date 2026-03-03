-- 脂付宝 (Keep) 初始数据库 Schema
-- 所有时间戳字段使用 Unix 毫秒

-- 用户表
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  email TEXT,
  nickname TEXT NOT NULL,
  avatar_url TEXT,
  role TEXT NOT NULL CHECK (role IN ('manager', 'checker')),
  manager_id TEXT REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_manager_id ON users(manager_id);

-- 管理者配置表
CREATE TABLE IF NOT EXISTS manager_configs (
  id TEXT PRIMARY KEY,
  manager_id TEXT NOT NULL UNIQUE REFERENCES users(id),
  checkin_days INTEGER NOT NULL DEFAULT 5,
  points_per_cycle INTEGER NOT NULL DEFAULT 1,
  bonus_cycles INTEGER NOT NULL DEFAULT 3,
  bonus_points INTEGER NOT NULL DEFAULT 1,
  points_expiry_days INTEGER NOT NULL DEFAULT 365,
  penalty_inactive_days INTEGER NOT NULL DEFAULT 10,
  penalty_points INTEGER NOT NULL DEFAULT 1,
  day_reset_hour INTEGER NOT NULL DEFAULT 5,
  is_configured INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- 邀请链接表
CREATE TABLE IF NOT EXISTS invitations (
  id TEXT PRIMARY KEY,
  manager_id TEXT NOT NULL REFERENCES users(id),
  code TEXT NOT NULL UNIQUE,
  is_used INTEGER NOT NULL DEFAULT 0,
  used_by TEXT REFERENCES users(id),
  created_at INTEGER NOT NULL,
  used_at INTEGER
);
CREATE INDEX idx_invitations_code ON invitations(code);

-- 打卡记录表
CREATE TABLE IF NOT EXISTS checkins (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  manager_id TEXT NOT NULL REFERENCES users(id),
  image_key TEXT NOT NULL,
  note TEXT,
  checkin_date TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_checkins_user_date ON checkins(user_id, checkin_date);
CREATE INDEX idx_checkins_manager_id ON checkins(manager_id);
CREATE UNIQUE INDEX idx_checkins_unique_daily ON checkins(user_id, manager_id, checkin_date);

-- 积分账本（支持FIFO消费）
CREATE TABLE IF NOT EXISTS points_ledger (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  manager_id TEXT NOT NULL REFERENCES users(id),
  source_type TEXT NOT NULL CHECK (source_type IN ('checkin_reward', 'bonus_reward')),
  source_id TEXT,
  original_amount INTEGER NOT NULL,
  remaining_amount INTEGER NOT NULL,
  earned_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX idx_points_ledger_user ON points_ledger(user_id, manager_id);
CREATE INDEX idx_points_ledger_fifo ON points_ledger(user_id, expires_at);

-- 积分流水（审计日志）
CREATE TABLE IF NOT EXISTS points_events (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  manager_id TEXT NOT NULL REFERENCES users(id),
  event_type TEXT NOT NULL CHECK (event_type IN ('earn_checkin', 'earn_bonus', 'redeem', 'cancel_redeem', 'expire', 'penalty')),
  amount INTEGER NOT NULL,
  related_order_id TEXT,
  description TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_points_events_user ON points_events(user_id, created_at);

-- 奖品表
CREATE TABLE IF NOT EXISTS prizes (
  id TEXT PRIMARY KEY,
  manager_id TEXT NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  image_key TEXT,
  points_required INTEGER NOT NULL,
  stock INTEGER NOT NULL DEFAULT 0,
  is_blind_box INTEGER NOT NULL DEFAULT 0,
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX idx_prizes_manager ON prizes(manager_id);

-- 兑换订单表
CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  manager_id TEXT NOT NULL REFERENCES users(id),
  prize_id TEXT NOT NULL REFERENCES prizes(id),
  points_spent INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'cancelled')),
  verify_code TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  verified_at INTEGER,
  cancelled_at INTEGER
);
CREATE INDEX idx_orders_user ON orders(user_id, created_at);
CREATE INDEX idx_orders_manager ON orders(manager_id);
CREATE INDEX idx_orders_verify_code ON orders(verify_code);

-- 订单积分消费明细（取消兑换时精确还原用）
CREATE TABLE IF NOT EXISTS order_points_usage (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  ledger_id TEXT NOT NULL REFERENCES points_ledger(id),
  amount INTEGER NOT NULL
);
CREATE INDEX idx_order_points_usage_order ON order_points_usage(order_id);
