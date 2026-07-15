## 问题陈述

RoseCloud 需要自用的 Workforce IAM：管理员工登录身份、多 Tenant 成员关系、组织内授权与平台运维。当前没有可落地的统一身份系统；业务模块无法安全地完成邀请加入、MFA、租户上下文鉴权与即时会话控制。首个可交付增量必须打通从平台引导到「Owner 邀请成员并以系统 Role 允许/拒绝」的端到端路径，同时把跨租户安全边界（全局 User 凭据 vs Tenant Membership）做成硬不变量。

## 方案

交付 RoseCloud IAM **薄切片（Thin Slice / Plan 0001）**：Java 25 + Spring Boot 4.1.0 模块化单体 + PostgreSQL；React + Vite 最小控制台（同站点代理）。PlatformOperator 经离线 CLI 一次性 setup；创建 PENDING Tenant；首位 Owner 接受 Invitation 并绑定 TOTP 后 Tenant 变为 ACTIVE；Owner 邀请 Admin/Member；User 登录获得 UserContext AccessToken，选择 Membership 后获得携带 Permission 的 TenantContext AccessToken；内置 Owner/Admin/Member 演示授权差异；Refresh Cookie 轮换与登出可用。审计写路径从第一天存在。完整 V1 行为以仓库 `docs/spec/iam-v1.md` 为准，本 PRD 仅交付薄切片范围。

术语一律遵循根目录 `CONTEXT.md`（User、Tenant、Membership、Principal、Permission、Role、Invitation、LoginSession、AccessToken、UserContext、TenantContext、AuditEvent、PlatformOperator、StepUp）。

## 用户故事

1. 作为待激活的 PlatformOperator，我希望用离线 CLI 签发一次性 setup token，以便首个 Operator 不会被开放的 HTTP 初始化接口抢占。
2. 作为待激活的 PlatformOperator，我希望在浏览器用 setup token 设置密码并绑定 TOTP，以便 Operator 在强制 MFA 下变为 ACTIVE。
3. 作为 PlatformOperator，我希望二次裸初始化失败，以便 setup 完成后无人能再创建竞争的平台根账号。
4. 作为 PlatformOperator，我希望用独立登录入口以密码 + TOTP 登录，以便 Operator 会话永不与 User 会话混用。
5. 作为 PlatformOperator，我希望创建 PENDING 状态的 Tenant 并邀请首位 Owner，以便不存在没有 Owner 的 ACTIVE Tenant。
6. 作为 PlatformOperator，我希望邀请邮件经 Outbox 入队，以便 SMTP 短暂不可用时 Tenant 创建仍能成功提交。
7. 作为受邀人，我希望接受 Owner Invitation、设置密码并绑定 TOTP，以便原子地成为 User + Owner Membership，且 Tenant 变为 ACTIVE。
8. 作为受邀人，我希望公开的邀请/登录/忘记密码响应防邮箱枚举，以便攻击者无法探测已注册邮箱。
9. 作为 Tenant Owner，我希望用内置 Role（Admin 或 Member）邀请他人，以便本切片无需自定义 Role UI 也能扩充成员。
10. 作为已存在的 User，我希望再次接受 Invitation 时创建新的 Membership（不复用已移除的），以便历史任期与旧角色不会静默恢复。
11. 作为 User，我希望用已验证邮箱 + 密码 + TOTP 登录，以便获得 UserContext AccessToken 与可轮换的 Refresh Cookie。
12. 作为处于 MFA 绑定门闸的 User，我希望在获得任何 TenantContext 之前必须完成 TOTP 绑定，以便强制 MFA 的 Tenant 无法被绕过。
13. 作为 User，我希望列出自己的 ACTIVE Membership 并选择其一，以便获得绑定该 `tenant_id` 与 `membership_id` 的 TenantContext AccessToken。
14. 作为 User，我希望 TenantContext 的 claim 携带展开后的 Permission code（而非 Role 名称），以便日后自定义 Role 改名不影响业务校验。
15. 作为 Member，我希望 `demo:read` 成功而 `demo:admin` 返回 403，以便内置 RBAC 可被明确验证。
16. 作为 Admin，我希望在 Member 失败的地方 `demo:admin`（及分配给 Admin 的邀请/暂停等演示权限）能成功，以便角色差异可见。
17. 作为 Owner，我希望拥有全部演示权限（含仅 Owner 的动作），以便控制面权限与 Admin 区分开。
18. 作为 User，我希望 AccessToken 寿命约 5 分钟，且每次刷新轮换 Refresh，以便被盗 AccessToken 很快过期并能检测 Refresh 重用。
19. 作为同时打开多个标签页的 User，我希望短宽限窗口内的并发刷新返回可重试而非撤销会话族，以便合法客户端不被误杀。
20. 作为 User，我希望宽限外的旧 Refresh 重用会撤销整个 LoginSession 会话族，以便轮换后被盗用的 Token 可被收敛。
21. 作为 User，我希望登出使 LoginSession 失效，以便之后刷新失败。
22. 作为 User，我希望 AccessToken 仅存内存、Refresh 仅存 Secure + HttpOnly + SameSite Cookie（经反向代理同站点），以便 XSS 无法把长期凭据固化进 localStorage。
23. 作为 Tenant Admin 或 Owner，我希望「禁用员工」只暂停 Membership，以便我不能关闭该 User 在其他 Tenant 的全局登录。
24. 作为 Tenant Admin 或 Owner，我必须不能重置他人的全局 TOTP，以便无法完成跨租户 MFA 接管。
25. 作为忘记密码的 User，我希望邮箱重置链接只改密码、不改/绕过 TOTP，以便邮箱被盗本身无法绕过 MFA。
26. 作为 PlatformOperator，我希望暂停 Tenant 后立即阻止该 Tenant 的新 TenantContext 签发与刷新，并接受 AccessToken 最多 5 分钟陈旧，以便无需给全平台轮换签名密钥即可切断访问。
27. 作为 User，我希望密码/邮箱/TOTP/恢复码变更会撤销我的全部 LoginSession，以便凭据变更后攻击者无法继续刷新。
28. 作为已被暂停或移除的 Membership，我希望无法再被选为 TenantContext，以便被暂停的人不能仅靠 Refresh 重新进入 Tenant。
29. 作为 Owner 或 Operator，我希望高风险操作（若本切片已暴露对应端点）前要求 StepUp（近 5 分钟内密码 + TOTP），以便被盗会话无法永久接管。
30. 作为执行认证或 IAM 变更的任意 Principal，我希望写入追加式 AuditEvent（业务 API 不可改删），以便即使没有审计控制台也有可调查事实。
31. 作为开发者，我希望用 Flyway 管理 schema，并用关闭 OSIV、默认 LAZY、Entity 不泄漏出模块的 JPA，以便 IAM 查询可控。
32. 作为开发者，我希望有 ArchUnit 包边界规则，以便 tenancy 不能直接触达 session 持久层，模块化单体边界得以维持。
33. 作为前端开发者，我希望从 OpenAPI 生成 TypeScript 客户端，以便 API 漂移在 CI 失败，而不是静默弄坏 SPA。
34. 作为薄切片的使用者，我希望有最小 React 路径覆盖 Operator setup、创建 Tenant、接受邀请、绑定 TOTP、登录、选择 Tenant、邀请成员与演示允许/拒绝页，以便可手工演示整条链路。
35. 作为 setup 完成后的 PlatformOperator，我希望始终至少保留一个 ACTIVE Operator（多 Operator 能力后续再补），以便平台不会被自己锁死——本切片可以只有首位 Operator，但不得提供清零 Operator 的路径。
36. 作为安全审阅者，我希望密码经 DelegatingPasswordEncoder（默认 Argon2id）哈希、Refresh/恢复码仅存哈希、TOTP Secret 带 `key_id` 的 AEAD 加密、JWT 使用带 `kid`/JWKS 的 RS256，以便存储泄露的影响半径可界定。
37. 作为容量规划者，我希望切片按 ≤10 万 User / ≤1 万 Tenant、约 500 RPS JWT/普通 API 峰值、约 20 RPS 密码登录峰值设计，以便不把 Argon2id 错误按「500 登录 RPS」估算。
38. 作为没有任何 Membership 的 User，我希望仍能使用 UserContext 管理安全设置与接受新邀请，以便失去最后一个 Membership 不会使认证被遗弃。

## 实现决策

- **本 PRD 范围**：仅 Plan 0001 薄切片（I0–I6）。与切片相交处，完整 V1 规则以 `docs/spec/iam-v1.md` 为不变量；延后的 V1 能力见「不在范围内」。
- **技术栈**：Java 25、Spring Boot **4.1.0**、Maven Wrapper、PostgreSQL、带严格性能护栏的 Spring Data JPA、Flyway、ArchUnit。前端：React + TypeScript + Vite，语义化 HTML + CSS Modules，无大型组件库。
- **仓库形态**：单仓库 `rosecloud-iam`；后端在根目录；`frontend/`；`docs/`。包根 `io.rosecloud.iam`，模块包：bootstrap、shared、identity、operator、tenancy、access、session、audit、delivery、api。
- **架构 ADR**：模块化单体 + 单 Postgres（ADR-0001）；全局 User + 多 Tenant Membership（ADR-0002）；短 Access JWT + 轮换 LoginSession Refresh（ADR-0003）。
- **标识**：UUIDv7 作为对内对外统一主键。
- **租户隔离**：共享 schema，显式 `tenant_id`，复合唯一/外键；V1 切片不做 RLS / schema-per-tenant。
- **认证令牌**：RS256 AccessToken ≤5 分钟；UserContext 与单 Tenant 的 TenantContext（展开 Permission）；Refresh 放 HttpOnly Cookie；服务端只存哈希；轮换；带短并发宽限的重用检测，窗外则撤销会话族；闲置 30 天 / 绝对 90 天；每 User 最多 10 个会话（切片需覆盖创建/刷新/登出）。
- **切片仅内置 RBAC**：Owner、Admin、Member 系统 Role；代码声明的演示 Permission；平面并集；无自定义 Role UI；无继承/deny/Group。
- **Invitation**：可预选内置 Role ID；接受时按当时 Role 内容生效；待处理邀请日后会阻止所引用自定义 Role 删除（本切片无自定义 Role 则不演练）。
- **MFA**：TOTP 属于全局 User；Owner 与 Operator 强制；Tenant 要求 MFA 时走绑定门闸；Tenant Admin 不能重置全局 TOTP。
- **PlatformOperator**：独立 Principal 空间；CLI 签发 setup token；HTTP 完成密码 + TOTP；禁止模拟登录。
- **邮件**：事务 Outbox + SMTP/开发适配器（Mailpit 或日志）。
- **审计**：认证与 IAM 变更追加写；本切片不要求审计控制台。
- **测试接缝（已确认）**：(1) HTTP/OpenAPI 为主；(2) Operator 离线 CLI 为辅。不以领域服务层作为产品契约接缝。
- **Issue tracker**：GitHub Issues；AFK 就绪使用标签 `ready-for-agent`。

## 测试决策

- **好的测试**只在已确认接缝断言外部行为：HTTP 状态/正文/Cookie/头，以及 CLI 退出码与可通过后续 HTTP 观察的副作用。不以私有方法调用、JPA 实体图、React 组件内部实现作为主证明。
- **主套件**：真实 HTTP + Testcontainers PostgreSQL 的 Spring Boot 集成测试，覆盖 Operator setup（CLI→HTTP）、Tenant PENDING→ACTIVE、双角色允许/拒绝、刷新轮换/登出、Membership 暂停阻止 TenantContext 选择、以及可行范围内的枚举防护。
- **次套件**：CLI 单元/集成测试，覆盖 setup token 签发与拒绝再次初始化。
- **护栏**：ArchUnit 包边界；登录/选择 Tenant/演示鉴权路径的查询数断言；OpenAPI ↔ 生成 TS 客户端的 CI 漂移检查。
- **前端**：可选 Playwright 冒烟最小 React 路径；不能替代 API 套件。
- **既有范例**：绿地项目，无遗留套件；模式见 `docs/plans/0001-thin-slice.md` 增量 I0–I6。

## 不在范围内

- 自定义 Role CRUD / 授予上限 UI（完整 V1 再做）
- 审计查询控制台
- 完整的 User 自助删除与匿名化产品流程
- Tenant 30 天清理 UI（可留状态挂点）
- 完整已泄露密码库、完整设备会话管理 UI
- 多密钥 JWT 轮换运维面板（双密钥数据结构可以；切片可用单活跃密钥）
- OIDC/SAML SSO、SCIM、Passkey/WebAuthn、机器身份（OAuth Client、服务账号、API Key）
- 部门/Group、角色继承、deny 规则、ABAC
- 模拟登录
- 公开注册 / 自助创建 Tenant
- 多实例高可用
- 合规认证交付（GDPR/SOC2/强监管）

## 补充说明

- 重叠规则的行为权威：`docs/spec/iam-v1.md` + ADR + `CONTEXT.md`。本 GitHub PRD 是面向 agent 的薄切片交付契约；应用 `/to-tickets` 按 Plan 0001 增量拆票。
- 容量说明：「认证峰值 500 RPS」指 JWT 校验 / 普通 API，不是 Argon2id 密码登录（登录峰值约 20 RPS）。
- 有意代价：撤销/暂停后 AccessToken 最多仍有效 5 分钟；测试中 TTL 应可缩短以便断言。
- 下一步技能：`/to-tickets`，为 I0–I6 创建带阻塞边的 GitHub issues。
