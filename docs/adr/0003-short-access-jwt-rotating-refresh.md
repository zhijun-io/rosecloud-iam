# Short Access JWT and rotating refresh

Status: accepted

在确认为单进程、同站点 SPA 的情况下，服务端 Session Cookie 更简单且可立即撤销。我们仍选择 **≤5 分钟 RS256 Access JWT + 服务端轮换 LoginSession（Refresh Cookie）**：AccessToken 的 TenantContext 携带展开后的 Permission，便于未来第一方独立服务离线验签；Refresh 只存哈希、轮换并做复用检测（短并发宽限后撤销会话族）。

浏览器约定：AccessToken 仅内存；Refresh 为 Secure/HttpOnly/SameSite Cookie；反向代理保证同站点，避免 `SameSite=None`。

已知代价：权限收回与暂停最多 5 分钟陈旧；必须维护密钥/JWKS、刷新竞态与会话族状态机。这是**有意预付**给「同仓多应用」的复杂度，不是当前单体的刚需。若产品长期永远不拆验证方，应重新评估改回服务端 Session。
