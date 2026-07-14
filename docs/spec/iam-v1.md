# RoseCloud IAM V1 Spec

行为规格。术语以根目录 `CONTEXT.md` 为准。本文件定义产品必须遵守的边界与不变量，不描述实现类名或表结构。

## 1. Product boundary

- 服务对象：RoseCloud 内部 Workforce IAM（组织员工身份），不是 CIAM，也不是对外通用 IdP 产品。
- 部署形态：模块化单体；单进程、单 PostgreSQL 权威库；首版单实例运行。
- 接入形态：前后端分离。后端 REST/JSON + OpenAPI；前端 React SPA 经同站点反向代理访问 `/api`。
- 身份权威：IAM 是 User / Membership / Credential 的权威源；首版不做目录同步。

## 2. Non-goals (V1)

明确不在首版与近期承诺范围内：

- OIDC / SAML 企业 SSO
- SCIM / 外部目录同步
- Passkey / WebAuthn
- 机器身份（OAuth Client、服务账号、API Key）
- 部门 / Group 及基于组的授权
- 角色继承、deny 规则、ABAC
- 平台模拟登录
- 公开注册与自助创建 Tenant
- 多实例高可用（首版明确单实例）

## 3. Actors

| Actor | 能做什么 |
| --- | --- |
| PlatformOperator | 创建/暂停 Tenant；邀请首位 Owner；停用跨租户影响的 User 凭据能力（如重置全局 TOTP）；管理其他 Operator；不能模拟 User |
| Tenant Owner | 控制该 Tenant：成员、角色分配、转让 Owner、发起 Tenant 关闭；须已绑定 TOTP |
| Tenant Admin | 在自身 Permission 上限内管理成员与自定义角色；不能授予超出自身的 Permission；不能授予 Owner |
| Tenant Member | 在已绑定的 Permission 范围内访问业务；默认可退出本 Tenant |
| User（无有效 Membership） | 仅 UserContext：管理自身安全设置、接受新邀请、查看/撤销自身会话 |

## 4. Lifecycles

### 4.1 User

1. 仅能通过有效 Invitation 创建；无公开注册。
2. 登录标识：已验证邮箱（大小写规范后全局唯一）；主键为不可变 UUIDv7。
3. 失去全部 Membership 后，User 仍然存在，只能使用 UserContext。
4. 自助删除前必须退出/移除全部 Membership；若仍是某 Tenant 的最后 Owner，必须先转移。
5. 删除：匿名化个人信息，保留随机 User ID 与相关 AuditEvent 事实。

### 4.2 Tenant

状态：`PENDING` → `ACTIVE` → `SUSPENDED` → `PENDING_DELETE` → purged。

1. 仅 PlatformOperator 可创建 Tenant，并邀请首位 Owner；创建后为 `PENDING`。
2. 首位 Owner 接受邀请并完成强制 TOTP 绑定后，Tenant 原子变为 `ACTIVE`。
3. PlatformOperator 可暂停：阻止新签发与刷新 TenantContext Token；已签发 AccessToken 最多再有效 5 分钟。
4. Owner 请求删除：进入 `PENDING_DELETE`，保留 30 天可撤销；期满后清理业务 PII，保留审计所需稳定标识。

### 4.3 Membership

1. 由接受 Invitation 创建（首次或再次加入均为新 Membership ID）。
2. 状态：`ACTIVE` / `SUSPENDED` / `REMOVED`。
3. Tenant Admin/Owner「禁用员工」= 暂停本 Tenant 的 Membership；不得禁用该 User 的全局登录（跨租户越权）。
4. 移除后角色绑定失效；再次邀请产生新 Membership，不复用旧角色绑定。
5. 同一 Tenant 内，同一 User 同时最多一个非 `REMOVED` Membership。

### 4.4 Invitation

1. 有过期时间；公开/未认证侧响应须防邮箱枚举（统一模糊成功语义）。
2. 可预选一个或多个 Role ID；待处理 Invitation 阻止所引用自定义 Role 删除。
3. 接受时按**当时** Role 内容取 Permission，不做 Permission 快照。
4. 新 User：设密码 → 验证邮箱（若流程需要）→ 若策略要求则绑定 TOTP → 生成 Membership。
5. 已存在 User：验证后直接生成 Membership（仍须满足目标 Tenant 的 MFA 策略）。

## 5. Authentication

### 5.1 Methods

- V1：密码 + TOTP。
- 新密码：Spring `DelegatingPasswordEncoder`，默认 Argon2id，保留算法版本升级能力。
- 密码策略：最少 12 字符；允许长密码与粘贴；**不强制定期轮换**；拦截常见/已泄露密码。
- TOTP Secret：应用层 AEAD 加密存储，密钥在库外，密文带 `key_id` 支持轮换。
- TOTP 归属：属于 User（全局一套），不属于 Membership。

### 5.2 MFA policy

- Tenant 可强制 MFA：密码验证通过后仅允许进入 TOTP 绑定门闸；绑定完成前不可进入业务 TenantContext。
- Owner 与 PlatformOperator **必须**绑定 TOTP；成为高权限角色前不得跳过。
- 未开启强制 MFA 的 Tenant：普通 Member 可自愿启用 TOTP。

### 5.3 Recovery

- 丢失 TOTP 设备：优先使用一次性恢复码；耗尽后**仅 PlatformOperator** 可重置全局 TOTP。
- Tenant Admin/Owner **不能**重置该 User 的全局 TOTP（跨租户安全边界）。
- 忘记密码：邮箱链接只允许重设密码，**不**清除或绕过 TOTP；之后登录仍需 TOTP/恢复码。
- Tenant Admin 可暂停 Membership、可要求成员重新登录，但不能替代全局 MFA 恢复。

### 5.4 Brute-force

- 按 User 标识 + IP 渐进限流/延迟；短时冷却；不采用永久锁定（避免蓄意拒绝服务）。

### 5.5 Enumeration

- 登录、忘记密码、接受邀请等公开流程：统一模糊响应。
- 已认证且具备租户权限的管理员查询：可返回精确结果。

## 6. Sessions and tokens

### 6.1 AccessToken

- RS256 签名，带 `kid`；提供 JWKS；支持双密钥滚动轮换。
- 寿命：5 分钟。
- 形态：
  - **UserContext**：证明 User，无 Tenant Permission。
  - **TenantContext**：绑定单个 `tenant_id` + `membership_id`，claim 中放展开后的 Permission code；切换 Tenant 重新签发。
- 浏览器：AccessToken 仅放内存；不写 localStorage。

### 6.2 LoginSession (Refresh)

- 绑定 User（或 Operator）与设备线索；**不**永久绑死单个 Tenant。
- Refresh Token：仅 `Secure + HttpOnly + SameSite` Cookie；库中只存哈希。
- 每次刷新轮换；检测复用：在极短并发宽限窗口内返回可重试冲突；窗口外旧 Token 重用则撤销整个会话族。
- 闲置过期 30 天；绝对过期 90 天。
- 同一 User 最多 10 个活跃会话；超出时撤销最久未使用者；用户可列出并逐个撤销。
- 签发 TenantContext AccessToken 时，显式选择目标 Membership 并校验：Tenant/Membership ACTIVE，且 MFA 策略已满足。

### 6.3 Revocation events

以下事件撤销该 User 的全部 LoginSession：

- 密码变更
- 邮箱变更
- TOTP/恢复码变更
- User 自助「退出所有设备」

以下事件撤销受影响范围内的续期能力：

- Membership 暂停/移除：阻止该 Tenant 的新 TenantContext 签发与该范围内续用
- Tenant 暂停：阻止该 Tenant 的新签发与刷新

AccessToken 最多存在 5 分钟陈旧窗口（含权限收回、Tenant/Membership 暂停）。

### 6.4 Step-up

转移 Owner、重置 MFA（Operator）、修改邮箱、关闭 Tenant 等高风险操作：要求最近 5 分钟内完成密码 + TOTP 再认证。

## 7. Authorization (RBAC)

1. Permission 由各业务模块在代码中声明；IAM 汇总供角色勾选；不可管理员任意创建字符串。
2. 每个 Tenant 内置系统 Role：`Owner`、`Admin`、`Member`；Owner Role 不可删除、不可改其 Permission 集合。
3. Membership 可绑定多个 Role；最终权限 = Permission 平面并集。
4. 无角色继承、无 deny、无 Group。
5. 自定义 Role 删除：必须先解除全部 Membership 绑定；若仍被待处理 Invitation 引用则拒绝删除。
6. 授予上限：操作者只能授予自己已拥有的 Permission；Owner Role 只能由现有 Owner 授予。
7. 业务判定应对 Permission code，不得对自定义 Role 名称写死。

## 8. PlatformOperator

1. 与 User 完全分离：独立登录入口、凭据、会话。
2. 首个 Operator：离线 CLI 生成一次性短期 setup token；浏览器完成设密 + TOTP 后激活；初始化成功后不可再次裸初始化。
3. 后续 Operator：现有 Operator 可邀请；能力集固定（不作平台侧 RBAC）。
4. 始终至少保留一个 ACTIVE Operator。
5. 禁止模拟登录。
6. 允许：停用身份、恢复 Tenant 所有权、重置 User 全局 TOTP；敏感操作需 reason + AuditEvent + StepUp。
7. 全部 Operator 丢失 MFA：仅服务器本地 CLI 可重置指定 Operator MFA，写审计并立即撤销其会话。

## 9. Audit

- 记录：全部认证事件结果 + IAM 管理变更（成功与失败中与安全相关者）。
- 不记录：每次授权判定的允许/拒绝洪流。
- 形态：应用层追加写；业务 API 无 update/delete。
- 在线保留 1 年。
- 首版不以哈希链或外部不可变存储为目标。

## 10. Delivery channels

- 邮件：业务事务提交 → Outbox 行 → 后台重试 → SMTP 适配器。
- 不得在请求线程内「提交成功依赖 SMTP 立即成功」。

## 11. Capacity and SLO

| 指标 | 目标 |
| --- | --- |
| User 规模 | ≤ 100,000 |
| Tenant 规模 | ≤ 10,000 |
| 普通 API / JWT 校验峰值 | 500 RPS |
| 密码登录峰值 | 20 RPS |
| 普通 API p95 | < 150 ms |
| Token 刷新 p95 | < 100 ms |
| 登录 p95 | < 500 ms |

灾难恢复：RPO ≤ 5 分钟，RTO ≤ 1 小时（PostgreSQL 持续 WAL 归档 + 定期全备 + 恢复演练）。

## 12. Compliance

- V1：通用安全基线；不承诺 GDPR 认证、SOC 2 或强监管等保交付物。
- 仍遵守本规格中的最小化删除（匿名化）与审计保留约定。

## 13. Thin-slice vs full V1

完整 V1 行为以本文为准。首个可交付增量见 `docs/plans/0001-thin-slice.md`：系统 Role（不含自定义 Role）、无完整审计控制台、最小 React 流程；但从第一天起写入 AuditEvent。
