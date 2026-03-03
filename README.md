# keep-server

**脂付宝 (Keep)** 后端 API 服务 —— 基于 Cloudflare Workers + Hono 构建。

## 技术栈

- **运行时**: Cloudflare Workers
- **框架**: [Hono](https://hono.dev/) v4
- **数据库**: Cloudflare D1 (SQLite)
- **缓存**: Cloudflare KV（验证码、Refresh Token）
- **存储**: Cloudflare R2（图片文件）
- **限流**: Cloudflare Rate Limiting（官方绑定）
- **语言**: TypeScript

## 快速开始

### 前置条件

- Node.js >= 18
- [Wrangler CLI](https://developers.cloudflare.com/workers/wrangler/)
- Cloudflare 账号（已创建 D1 / KV / R2 资源）

### 安装依赖

```bash
npm install
```

### 配置 Cloudflare 资源

编辑 `wrangler.jsonc`，填入你的真实资源 ID：

```jsonc
{
  "d1_databases": [{ "database_id": "你的D1数据库ID" }],
  "kv_namespaces": [{ "id": "你的KV命名空间ID" }],
  "r2_buckets": [{ "bucket_name": "你的R2存储桶名" }]
}
```

### 设置密钥

```bash
# 生成并设置 JWT 密钥（随机字符串，生产环境必须设置）
wrangler secret put JWT_SECRET
```

### 初始化数据库

```bash
# 本地开发
wrangler d1 execute mzcode-d1 --local --file=migrations/0001_initial_schema.sql

# 远程生产
wrangler d1 execute mzcode-d1 --remote --file=migrations/0001_initial_schema.sql
```

### 本地开发

```bash
npm run dev
# 服务启动在 http://localhost:8787
```

### 部署

```bash
npm run deploy
# 或通过 GitHub 推送自动部署到 Cloudflare Workers
```

## API 概览

基础路径：`/api`

| 模块 | 路径 | 说明 |
|------|------|------|
| Health | `GET /api/health` | 健康检查 |
| Auth | `/api/auth/*` | 注册、登录、Token 刷新、用户信息 |
| Files | `GET /api/files/:path` | R2 文件代理 |
| Manager | `/api/manager/*` | 管理者配置、邀请链接、打卡者列表 |
| Checkin | `/api/checkin/*` | 打卡、状态查询、历史记录、日历 |
| Points | `/api/points/*` | 积分汇总、流水明细 |
| Prizes | `/api/prizes/*` | 奖品管理、商城、兑换 |
| Orders | `/api/orders/*` | 订单列表、取消、核销 |

## 项目结构

```
src/
├── index.ts              # 应用入口
├── types/index.ts        # 类型定义
├── middleware/            # 中间件（CORS、鉴权、错误处理、限流）
├── routes/               # API 路由模块
├── services/points.ts    # 积分核心逻辑
└── utils/                # 工具函数（JWT、加密、验证码、日期）
migrations/
└── 0001_initial_schema.sql  # D1 数据库初始化
```

## 相关项目

- [keep-frontend](https://github.com/mz/keep-frontend) — 前端 H5 PWA 应用

## License

Private
