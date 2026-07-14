# Plan 0001: Thin slice

首个可运行纵向切片。完整行为以 [`docs/spec/iam-v1.md`](../spec/iam-v1.md) 为准；本切片**刻意缩小**范围，但仍须遵守已确认的安全不变量（分离 Operator、TOTP 归属 User、枚举防护、审计写路径、Refresh 轮换等）。

## Goal

端到端打通：

`Operator setup → 创建 PENDING Tenant → Owner 接受邀请并绑定 TOTP → Tenant ACTIVE → Owner 邀请 Admin/Member → 登录 → 选择 Tenant → 系统 Role 允许/拒绝 → 刷新与退出`

交付物：可运行的后端 API + 最小 React 流程 + OpenAPI 生成客户端 + 自动化测试能证明上述路径。

## In scope

- PlatformOperator：离线 CLI 一次性 setup token、设密、强制 TOTP、登录
- Tenant：创建、PENDING→ACTIVE（随首位 Owner 完成）、Operator 暂停（可测 API，UI 可选）
- Invitation：Owner 首邀 + 后续成员邀请（可预选内置 Role）
- User：仅邀请创建；密码 + TOTP；UserContext / TenantContext AccessToken
- LoginSession：轮换 Refresh、Cookie、最多 10 会话中的基础创建/刷新/撤销全部
- RBAC：仅内置 Owner / Admin / Member + 少量模块声明的演示 Permission
- AuditEvent：关键路径追加写入（无可视化控制台亦可）
- Outbox + 可替换的 SMTP/开发邮件适配器（本地可用 Mailpit 或日志适配器）
- ArchUnit 包边界骨架；Flyway 初始 schema
- 最小 React：对应上述屏幕，原生 CSS

## Out of scope (this slice)

- 自定义 Role、角色编辑 UI
- 审计查询控制台
- User 自助删除/匿名化完整流程
- Tenant 30 天关闭流程 UI（状态机可留挂点）
- 密码泄露库全量拦截（可先做最小长度 + 小 blocklist）
- 完整设备会话管理 UI（API 列表可后续）
- JWKS 多密钥滚动的运维面板（实现双密钥数据结构即可，切片可用单活跃密钥）
- 任何 SSO / SCIM / Passkey / 机器身份

## Increments

每个增量必须：**可合并、有测试、不破坏前序路径**。

### I0 — Skeleton

- Maven Wrapper、Java 25、Spring Boot 4.1.0、Flyway 空迁、健康检查
- 包结构 + ArchUnit 示例规则
- `frontend/` Vite React TS；代理 `/api`；OpenAPI 生成流水线占位
- **验收**：`./mvnw test` 与前端 `typecheck` 通过

### I1 — Operator bootstrap

- CLI：生成一次性 setup token（短期）
- HTTP：setup 设密 + 绑定 TOTP → ACTIVE Operator
- Operator 登录 → UserContext 等价的 Operator AccessToken + Refresh Cookie
- 禁止二次裸初始化
- **验收**：集成测试完整引导；重复初始化失败；无 TOTP 不能完成

### I2 — Tenant create + Owner invitation

- Operator 创建 Tenant（`PENDING`）+ Owner Invitation（Outbox 出邮件/日志）
- 公开接受邀请 API：模糊枚举响应；设密；强制 TOTP；创建 User + Owner Membership；Tenant → `ACTIVE`
- **验收**：未接受前 Tenant 非 ACTIVE；接受后 Owner 可登录；邮件至少入 Outbox

### I3 — Sessions and tenant selection

- Refresh 轮换、复用宽限、登出
- 选择 Membership 签发 TenantContext（Permission 并集）
- Membership/Tenant 非 ACTIVE 时拒绝签发
- **验收**：刷新拿到新 AccessToken；登出后刷新失败；暂停 Membership 后无法选入该 Tenant（AccessToken 陈旧窗口内旧 token 仍可用 ≤5min，测试可调短 TTL）

### I4 — Member invite + builtin RBAC

- Owner 邀请第二人，预选 Admin 或 Member
- 声明至少两个演示 Permission，例如 `demo:read`、`demo:admin`
- Owner 全开；Admin 含管理类演示权；Member 仅 `demo:read`
- 受保护演示端点：有权 200 / 无权 403
- **验收**：同 Tenant 下 Admin 与 Member 行为差异可测；不能从 Member 自助提权

### I5 — Minimal React path

- 页面贯通 I1–I4（拷贝邀请链接可接受，SMTP 用开发适配器）
- AccessToken 内存；Refresh 依赖 Cookie；同站点假设
- **验收**：手工脚本或 Playwright 冒烟：Operator→Tenant→Owner→Member→403/200

### I6 — Hardening pass

- 登录限流基础版、StepUp 挂在转让 Owner（若本切片实现转让）或至少挂在 Operator 重置路径的接口级钩子
- AuditEvent 覆盖：登录成功/失败、邀请、角色绑定、刷新复用撤销
- README：本地启动、CLI、Mail、代理
- **验收**：对照 `iam-v1.md` 切片范围 checklist 全部勾选；无 P0 安全回退（TOTP 可被 Tenant Admin 重置、Refresh 明文、公开枚举等）

## Suggested permission seed (demo)

| code | Owner | Admin | Member |
| --- | --- | --- | --- |
| `demo:read` | ✓ | ✓ | ✓ |
| `demo:admin` | ✓ | ✓ | |
| `tenant:invite` | ✓ | ✓ | |
| `tenant:membership:suspend` | ✓ | ✓ | |
| `tenant:owner:transfer` | ✓ | | |

（正式 Permission 目录在后续切片由真实业务模块替换；命名稳定优于完美。）

## Test strategy

- 单元：密码策略、Permission 并集、会话复用检测状态机
- 集成（Testcontainers PostgreSQL）：I1–I4 API 路径
- ArchUnit：禁止 `tenancy` 直接依赖 `session` 的 persistence 等
- 契约：OpenAPI 与生成客户端在 CI 无 diff
- 查询数：登录后选择 Tenant + 演示授权路径无 N+1

## Definition of done

1. 本地一条命令可起 API + 前端代理环境（compose 可选）。
2. 集成测试覆盖 Operator 引导、Tenant 激活、双角色授权差异、刷新与登出。
3. 文档交叉链接：本计划、规格、架构、三个 ADR、`CONTEXT.md` 术语一致。
4. 无自定义 Role、无审计 UI，但审计写路径存在且可在库中查询到事件。
5. 未引入 V1 non-goals 中的协议与身份类型。

## Follow-ups (not this plan)

- 自定义 Role + 授予上限完整 UI
- 恢复码与 Operator 重置 TOTP 流程产品化
- Tenant 关闭 30 天、User 匿名化
- 审计查询、会话设备列表 UI
- 多密钥 JWT 轮换演练与备份恢复演练记录
