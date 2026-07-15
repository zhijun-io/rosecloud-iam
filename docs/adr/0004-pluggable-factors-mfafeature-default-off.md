# Pluggable Factors behind MfaFeature (default off)

Status: accepted（行为路径已实现；FactorProvider SPI 仍为草图）

RoseCloud IAM 需要可扩展的第二因素，同时避免把「平台是否启用 MFA」做成 Permission/Capability，也避免首版就做租户强制 MFA。我们决定：用平台布尔开关 **MfaFeature**（默认关）控制是否发起 FactorChallenge 与是否允许新建 FactorBinding；用可插拔 **Factor**（首版仅 TOTP）表达种类；用 **FactorBinding** 表达某 Principal 的已绑定实例。登录与 StepUp 共享「有 Binding 则客户端选定后 FactorChallenge，否则仅密码」规则。RecoveryCode 是专用恢复路径，不是 Factor。

拒绝「全程强制 TOTP」与「Tenant required MFA」作为本修订默认：薄切片硬编码强制绑定不作为完整 V1 前进方向。拒绝把 MfaFeature 叫 Capability：该词已留给 Permission 的 avoid 列表。

模块缝：**FactorProvider** / **FactorCatalog**（含 MfaFeature）为 identity 侧 SPI 草图；可运行原型见 `src/prototype/factor-provider`（未接入生产登录路径）。Operator 与 User 共用同一 challenge / bind 形状，政策差异不进 provider。

行为继承：已验证 TOTP ⇒ 恰好一条 kind=`totp` 的 FactorBinding（稳定 binding id；无需重绑）；pending bind ≠ Binding。Flyway `V5__mfa_feature.sql` 承载 platform_setting / challenge / recovery / nullable TOTP / `step_up_satisfied_at`。
