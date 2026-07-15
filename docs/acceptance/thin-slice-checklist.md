# Thin-slice acceptance checklist (Plan 0001 / I6)

对照 [`docs/spec/prd-thin-slice.md`](../spec/prd-thin-slice.md) 与 [`docs/plans/0001-thin-slice.md`](../plans/0001-thin-slice.md)。自动化优先；手工见 [`docs/local-dev.md`](../local-dev.md) I5 console smoke。

## Happy path

| Item | Proof |
| --- | --- |
| Operator CLI setup token → begin/complete → login | `OperatorBootstrapIT` |
| Create PENDING tenant + owner invite (outbox) | `TenantOwnerInviteIT` |
| Owner accept + TOTP → Tenant ACTIVE | `TenantOwnerInviteIT` |
| User login / refresh rotate / logout | `UserSessionIT` |
| Select ACTIVE membership → TenantContext + permissions | `UserSessionIT` |
| Owner invites Admin/Member; demo 200/403 | `MemberInviteRbacIT` |
| Minimal React console path | Manual: `docs/local-dev.md` I5 checklist |
| Vite same-origin `/` + `/api` proxy | `frontend/vite.config.ts` + local-dev |

## Negative / security

| Item | Proof |
| --- | --- |
| Failed login uses generic message + audit | `UserSessionIT.loginFailureUsesGenericMessageAndAuditsFailure` |
| Login progressive cooldown by email + IP (not permanent lock) | `LoginRateLimitIT` |
| Suspended membership cannot mint new TenantContext | `UserSessionIT.suspendedMembershipCannotBeSelectedForTenantContext` |
| Stale AccessToken works until short TTL expires after suspend | `StaleAccessTokenIT.suspendedMembershipKeepsExistingTenantTokenUntilAccessTtlExpires` |
| Stale AccessToken works until short TTL expires after logout (refresh revoked) | `StaleAccessTokenIT.logoutRevokesRefreshButAccessTokenWorksUntilAccessTtlExpires` |
| Refresh reuse outside grace revokes family | `UserSessionIT.oldRefreshReuseOutsideGraceRevokesWholeFamily` |
| Invitation accept anti-enumeration | `TenantOwnerInviteIT` |
| Tenant cannot reset another user's global TOTP | `MemberInviteRbacIT.tenantOwnerCannotResetAnotherUsersGlobalTotp` |
| Member cannot self-elevate / invite | `MemberInviteRbacIT` |
| AccessToken not in `localStorage` / `sessionStorage` | `npm run smoke:storage` / `task typecheck` |
| Refresh cookie Secure + HttpOnly (config) | `application.yml` + RefreshCookieFactory tests/path |

## AuditEvent coverage (thin slice)

| Event family | Proof |
| --- | --- |
| Operator setup + login success/fail | `OperatorBootstrapIT` |
| User login / refresh / logout / tenant context | `UserSessionIT` |
| Refresh reuse revoked | `UserSessionIT` |
| Tenant created / owner activated / member invited / invitation accepted | Owner path + `MemberInviteRbacIT.inviteAndAcceptanceWriteAuditEvents` |

Role bind is recorded as `tenant.invitation_accepted` details (role code in payload/details) — no separate `role.assigned` event in this slice.

## P0 regressions (must remain false)

- [x] Tenant Admin/Owner cannot reset global TOTP (`403`)
- [x] Refresh stored hashed server-side; cookie not returned as API plaintext body
- [x] Public invite/login responses do not enumerate account existence beyond generic errors
- [x] Frontend never persists AccessToken in Web Storage

## Explicitly out of scope (V1 non-goals for this slice)

SSO / SCIM / Passkey / machine identity / custom Role UI / audit query console — not introduced.

## Local mail adapter

Thin slice uses **outbox-only** delivery (`outbox_message`). No SMTP worker in-process. Inspect invite tokens with SQL or admin tools; see README / local-dev.
