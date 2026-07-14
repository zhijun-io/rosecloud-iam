# RoseCloud IAM

RoseCloud 自用的 Workforce IAM：管理员工登录身份、租户成员关系、组织内授权与平台运维。

## Language

### Principals

**User**:
全局员工登录身份。以不可变 ID 标识，可验证邮箱为可变登录属性；一个 User 可加入多个 Tenant。
_Avoid_: Account, Identity, 全局账号

**PlatformOperator**:
平台运维主体。与 User 分离的 Principal、凭据、登录入口与会话；不参与 Tenant RBAC，不能模拟登录。
_Avoid_: SuperAdmin, 超级管理员, system user

**Principal**:
当前已认证主体；运行时可能是 User 或 PlatformOperator。
_Avoid_: Subject（口语可用，代码与文档统一用 Principal）

**Credential**:
证明身份所需的秘密材料，如密码哈希、TOTP 密钥、恢复码。按所有者（User 或 PlatformOperator）归属。
_Avoid_: 密码记录（过窄）, AuthFactor（过宽）

### Tenancy

**Tenant**:
组织租户边界。成员关系、角色、邀请与租户级策略都挂在 Tenant 下。
_Avoid_: Organization, Org, Workspace, Company

**Membership**:
User 在某个 Tenant 内的一段任职关系。移除后再次加入必须创建新 Membership；暂停只影响本 Tenant。
_Avoid_: Member（指人时用 User；指关系时用 Membership）, Affiliation, TenantUser

**Invitation**:
有期限的加入请求。受邀人验证邮箱后创建或关联 User，并生成 Membership；可预选 Role，接受时按当时 Role 内容生效。
_Avoid_: Invitee, InviteCode（实现细节）, JoinRequest

### Authorization

**Permission**:
由业务模块在代码中声明的、可检查的操作权标识（如 `user:disable`）。不能由管理员自由发明。
_Avoid_: Capability, Authority, Privilege（口语可混用，规范词为 Permission）

**Role**:
Tenant 内 Permission 的命名集合。Membership 可绑定多个 Role，最终权限为平面并集；不支持继承与 deny。
_Avoid_: Group（首版不存在）, Profile, PermissionSet

**Owner**:
Tenant 的控制权角色，且是不可删除的系统 Role。每个 Tenant 至少一个 Owner；最后一个 Owner 不可退出或禁用，须先转移。
_Avoid_: Root, SuperUser（租户内）

**Admin**:
内置系统 Role。可管理本 Tenant 成员与角色，但授予权限不得超过自身已有 Permission；不能授予 Owner Role。
_Avoid_: Manager

**Member**:
内置系统 Role。表示普通成员，默认无管理 Permission。
_Avoid_: UserRole, BasicRole

### Session and Tokens

**LoginSession**:
服务端记录的刷新会话族状态：绑定 User（或 Operator）与设备线索，持有轮换后的 Refresh Token 哈希，负责续期与撤销。
_Avoid_: RefreshSession, DeviceSession（口语可说设备会话）

**AccessToken**:
短期 JWT。单次请求鉴权用；要么携带受限的 User 上下文，要么绑定一个 Tenant 与 Membership 并携带展开后的 Permission。
_Avoid_: JWT（技术载体，不是领域概念）, BearerToken

**UserContext**:
已认证但尚未选定（或当前无有效）Tenant 时的受限访问上下文；用于账号安全与租户选择，不能行使 Tenant 权限。
_Avoid_: GlobalToken, AnonymousSession

**TenantContext**:
AccessToken 绑定某个 Tenant 与 Membership 后的授权上下文；切换 Tenant 时重新签发。
_Avoid_: OrgContext, ActiveTenant

### Assurance

**AuditEvent**:
不可变、追加写的安全与管理事实记录（认证结果、IAM 变更等）。业务接口不可改删。
_Avoid_: Log, Activity, Trail

**StepUp**:
对已登录会话执行高风险操作前，要求近期完成密码与 TOTP 再认证。
_Avoid_: Reauth, Challenge（实现用语）, MFAGate
