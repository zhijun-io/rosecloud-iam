# Global User and multi-tenant Membership

Status: accepted

同一个自然人可以属于多个 Tenant。我们把**登录身份（User）与租户任职（Membership）拆开**：邮箱在 User 上全局唯一且须验证；权限、暂停、邀请都挂在 Membership/Tenant 上。Tenant Admin「禁用员工」只暂停本 Tenant 的 Membership，不能关闭该 User 在其他 Tenant 的登录。

拒绝「每租户一套账号」：同一邮箱多套密码与 MFA，运维与用户体验成本高。拒绝「首版禁止跨租户」：后期放开需要数据迁移，而 RoseCloud 内部平台从一开始就需要跨组织协作可能。

后果：密码与 FactorBinding（含 TOTP）属于 User（全局一套）；因此 **Tenant 侧不得重置全局 MFA 凭据**，丢失恢复能力由 PlatformOperator 处理。无 Membership 的 User 仍保留，仅能持 UserContext 处理安全设置与新邀请。再次加入 Tenant 一律新建 Membership，避免两段任职与旧角色混淆。前进方向见 ADR `0004-pluggable-factors-mfafeature-default-off`。
