# PROJECT_CONTEXT - keep-server

> 本文档供 AI 助手快速恢复项目上下文，非用户文档。

## 项目愿景

**脂付宝 (Keep)** —— 家庭运动激励工具。

一对一模式：管理者（老婆）设定打卡规则和奖品，打卡者（我）每日运动拍照打卡，累积积分兑换奖品。通过游戏化机制（连续打卡、全勤奖励、惩罚）培养运动习惯。

中文名：脂付宝 | 英文名：Keep | 品牌色：绿 #2E7D32 / 金 #D4A843

## 核心业务逻辑

### 角色

| 角色 | 说明 |
|------|------|
| manager | 管理者，配置规则、维护奖品、核销订单 |
| checker | 打卡者，通过邀请链接注册绑定到 manager |

### 打卡周期与积分

- **逻辑日分界**：每天凌晨 `day_reset_hour`（默认 5 点）为日期分界线，5 点前打卡算前一天
- **周期奖励**：连续打卡 `checkin_days`（默认 5）天为一个周期，完成获得 `points_per_cycle`（默认 1）积分
- **全勤奖励**：连续完成 `bonus_cycles`（默认 3）个周期，额外获得 `bonus_points`（默认 1）积分，可多次获得
- **积分有效期**：获取后 `points_expiry_days`（默认 365）天过期，FIFO 消费（优先使用最快过期的积分）
- **惩罚机制**：连续 `penalty_inactive_days`（默认 10）天未打卡，每满 10 天扣 `penalty_points`（默认 1）积分，最低扣到 0
- **中断重置**：连续打卡中断则周期进度、全勤进度全部归零

### 奖品兑换

- 管理者维护奖品列表（名称、积分、库存、图片、盲盒标记）
- 打卡者用积分兑换，FIFO 消费积分
- 兑换后生成订单 + 核销码（12 位大写），管理者扫码核销
- 取消兑换时精确还原积分到原账本记录，若积分已过期则提示用户

### 邀请机制

- 管理者必须先完成基础配置（`is_configured = 1`）才能生成邀请链接
- 邀请码一次性有效，绑定后失效
- 一个管理者可绑定多个打卡者

## 技术栈

| 项 | 技术 |
|----|------|
| 运行时 | Cloudflare Workers |
| 框架 | Hono v4 |
| 语言 | TypeScript |
| 数据库 | Cloudflare D1 (SQLite) |
| KV 存储 | Cloudflare KV（验证码、Refresh Token） |
| 对象存储 | Cloudflare R2（头像、打卡照片、奖品图片） |
| 图片处理 | Cloudflare Images |
| 限流 | Cloudflare RateLimit（官方 ratelimits 绑定） |
| 部署 | GitHub → Cloudflare Pages/Workers 自动部署 |

## 数据库结构 (D1)

9 张表，所有时间戳字段使用 Unix 毫秒：

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `users` | 用户 | id, username, password_hash, salt, role, manager_id |
| `manager_configs` | 管理者配置 | manager_id, checkin_days, points_per_cycle, bonus_cycles, bonus_points, points_expiry_days, penalty_inactive_days, penalty_points, day_reset_hour, is_configured |
| `invitations` | 邀请链接 | manager_id, code, is_used, used_by |
| `checkins` | 打卡记录 | user_id, manager_id, image_key, note, checkin_date |
| `points_ledger` | 积分账本(FIFO) | user_id, source_type, original_amount, remaining_amount, earned_at, expires_at |
| `points_events` | 积分流水(审计) | user_id, event_type, amount, description |
| `prizes` | 奖品 | manager_id, name, points_required, stock, is_blind_box, is_active |
| `orders` | 兑换订单 | user_id, prize_id, points_spent, status(pending/verified/cancelled), verify_code |
| `order_points_usage` | 订单积分明细 | order_id, ledger_id, amount（取消兑换时精确还原用） |

### 关键索引

- `checkins`: `UNIQUE(user_id, manager_id, checkin_date)` 防重复打卡
- `points_ledger`: `(user_id, expires_at)` 支持 FIFO 查询
- `orders`: `UNIQUE(verify_code)` 核销码唯一

## Cloudflare 绑定

| 绑定名 | 类型 | 用途 |
|--------|------|------|
| `DB` | D1Database | 主数据库 |
| `KV` | KVNamespace | 验证码(5min TTL)、Refresh Token(90天) |
| `R2` | R2Bucket | 文件存储 |
| `IMAGES` | Images | 图片变换 |
| `AUTH_LIMITER` | RateLimit | 认证接口限流 |
| `CHECKIN_LIMITER` | RateLimit | 打卡接口限流 |
| `GENERAL_LIMITER` | RateLimit | 通用接口限流 |
| `JWT_SECRET` | Secret | JWT 签名密钥（通过 wrangler secret put 设置） |
| `FRONTEND_URL` | Var | 前端域名，用于 CORS |

## API 结构

基础路径：`/api`

| 模块 | 路径前缀 | 限流 | 鉴权 | 说明 |
|------|----------|------|------|------|
| health | `/api/health` | 无 | 无 | 健康检查 |
| auth | `/api/auth/*` | AUTH_LIMITER | 部分 | 注册/登录/Token刷新/用户信息 |
| files | `/api/files/*` | 无 | 无 | R2 文件代理 |
| manager | `/api/manager/*` | GENERAL | 是+manager | 配置/邀请/打卡者列表 |
| checkin | `/api/checkin/*` | CHECKIN | 是 | 打卡/状态/历史/日历 |
| points | `/api/points/*` | GENERAL | 是 | 积分汇总/流水 |
| prizes | `/api/prizes/*` | GENERAL | 是 | 奖品CRUD/商城/兑换 |
| orders | `/api/orders/*` | GENERAL | 是 | 订单列表/取消/核销 |

### 认证方案

- 密码：SHA-256(salt + password)
- Access Token：JWT HS256，15 分钟有效
- Refresh Token：随机 hex，存 KV，90 天有效，刷新时轮换
- 验证码：SVG 生成，KV 存储，5 分钟 TTL

## 核心数据流

### 打卡 → 积分结算

```
POST /api/checkin
  → 验证今日未打卡 (UNIQUE 索引)
  → 上传图片到 R2 (checkins/{managerId}/{date}/{id}.jpg)
  → INSERT checkins
  → checkAndApplyPenalty()  // 惩罚检查
      → 计算距上次打卡间隔天数
      → 每满 penalty_inactive_days 天扣 penalty_points
      → FIFO 扣减 points_ledger + 记录 points_events
  → settlePointsAfterCheckin()  // 奖励结算
      → getLogicalDate(now, resetHour, +8) 计算今日日期
      → calculateConsecutiveDays() 计算连续天数
      → consecutiveDays % checkin_days === 0 → 周期奖励
        → INSERT points_ledger + points_events
      → completedCycles % bonus_cycles === 0 → 全勤奖励
        → INSERT points_ledger + points_events
```

### 兑换 → 积分消费

```
POST /api/prizes/redeem/:id
  → 乐观锁扣库存: UPDATE prizes SET stock = stock - 1 WHERE stock > 0
    → meta.changes === 0 → 库存不足
  → expirePoints() 过期处理
  → getAvailablePoints() 检查积分
    → 不足 → 回滚库存 + 报错
  → FIFO 消费 points_ledger (expires_at ASC)
    → 条件更新: WHERE remaining_amount >= consume 防并发超扣
  → INSERT orders + order_points_usage + points_events
```

### 取消兑换 → 积分还原

```
POST /api/orders/cancel/:id
  → 状态必须是 pending
  → 通过 order_points_usage 精确还原每条 ledger 的 remaining_amount
  → 检查已还原的积分是否已过期 → 过期的积分标记并记录 expire 事件
  → 库存 +1
  → 订单状态 → cancelled
```

## 目录结构

```
src/
├── index.ts              # 入口，挂载全局中间件和路由
├── types/index.ts        # Env 接口、所有数据库 Row 类型
├── middleware/
│   ├── auth.ts           # JWT 验证 + 角色检查
│   ├── cors.ts           # CORS 配置
│   ├── error-handler.ts  # AppError 类 + 全局错误处理
│   └── rate-limit.ts     # Cloudflare RateLimit 中间件
├── utils/
│   ├── jwt.ts            # HS256 JWT 签发/验证 (Web Crypto)
│   ├── crypto.ts         # 密码哈希、ID 生成、Token 生成
│   ├── captcha.ts        # SVG 验证码生成
│   └── date.ts           # 逻辑日期计算（含时区偏移 UTC+8）
├── services/
│   └── points.ts         # 积分结算、惩罚、过期、FIFO 扣减
├── routes/
│   ├── index.ts          # 路由聚合 + 限流中间件分配
│   ├── auth.ts           # 认证：注册/登录/刷新/登出/个人信息
│   ├── checkin.ts        # 打卡：创建/状态/历史/日历/管理者查看
│   ├── files.ts          # R2 文件代理
│   ├── health.ts         # 健康检查
│   ├── manager.ts        # 管理者：配置CRUD/邀请/打卡者列表
│   ├── points.ts         # 积分：汇总/流水
│   ├── prizes.ts         # 奖品：CRUD/商城/兑换
│   └── orders.ts         # 订单：列表/取消检查/取消/核销/管理
└── migrations/
    └── 0001_initial_schema.sql
```

## 开发约定

### 命名规范

- 数据库字段：`snake_case`
- TypeScript 接口：`PascalCase` + `Row` 后缀（如 `UserRow`、`OrderRow`）
- API 路径：`/api/{module}/{action}` REST 风格
- R2 存储路径：`{类型}/{managerId}/{子路径}`
  - 头像：`avatars/{userId}/{id}.{ext}`
  - 打卡：`checkins/{managerId}/{date}/{id}.{ext}`
  - 奖品：`prizes/{managerId}/{id}.{ext}`

### 错误处理

- 业务错误统一使用 `AppError(statusCode, message)` 抛出
- 全局 `errorHandler` 中间件捕获，返回 `{ success: false, error: string }`
- 成功响应：`{ success: true, data: T }`

### 时区处理

- Workers 运行在 UTC 环境
- `getLogicalDate()` 接受 `timezoneOffset` 参数，默认 `+8`（中国标准时间）
- 所有日期比较使用 UTC 方法（`getUTCFullYear` 等），通过偏移量转为本地时间

### 安全

- `JWT_SECRET` 通过 `wrangler secret put` 设置，不写入代码
- 密码使用 salt + SHA-256 哈希
- Refresh Token 存 KV 并支持轮换，旧 token 立即失效
- 所有写操作有 Rate Limit 保护

## Roadmap

### 已完成 (v0.1)

- [x] 基础设施：Hono 框架、中间件体系、D1 Schema
- [x] 认证系统：注册/登录/JWT/Refresh Token/SVG 验证码
- [x] 管理者配置与邀请系统
- [x] 打卡核心功能（拍照上传、逻辑日期、状态查询）
- [x] 积分系统（周期结算、全勤奖励、过期、惩罚、FIFO）
- [x] 奖品管理与兑换商城（乐观锁防超扣）
- [x] 订单管理与核销（QR 码核销、取消还原）
- [x] Cloudflare 官方 Rate Limit 集成
- [x] 代码审查与 Bug 修复（时区、并发、验证）

### 待开发

- [ ] PC 端超级管理员后台
- [ ] 补卡功能
- [ ] 打卡照片相关的扩展功能
- [ ] 单元测试覆盖（当前 test/index.spec.ts 是脚手架默认）
- [ ] 数据库迁移版本管理
