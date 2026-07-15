# Local development

本地开发约定：时区、Task、Docker Compose、可选 SDKMAN。架构总览见 [`architecture.md`](architecture.md)。

## Prerequisites

| Tool | Notes |
| --- | --- |
| **JDK 25** | 必须出现在当前 shell 的 `JAVA_HOME` / `PATH`。`task` **不会**切换 JDK。 |
| Maven Wrapper | 用 `./mvnw`（已钉 **3.9.16**）；不要用系统/SDKMAN 装的 Maven 跑本仓。 |
| [Task](https://taskfile.dev/) 3.x | 本地入口：`task --list`。 |
| Docker Compose | Postgres 16（`task up`）。 |
| Node 22 + npm | 前端 `task typecheck` / `task frontend:dev`。 |

可选：[SDKMAN](https://sdkman.io/) + 仓库根 `.sdkmanrc`（见下）。

## Timezone (`Asia/Shanghai`)

全栈默认 **`Asia/Shanghai`**，避免 JVM / Postgres / 序列化各用各的时区：

| 层 | 机制 |
| --- | --- |
| Task / 本机跑应用 | `Taskfile.yml` → `TZ`、`JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai` |
| Spring | `spring.jackson.time-zone`、`hibernate.jdbc.time_zone` |
| Compose Postgres | `TZ`、`PGTZ` |
| 测试容器 | `ApplicationHealthTest` 同步注入 `TZ` / `PGTZ` |
| CI | backend job 同样设置 `TZ` / `JAVA_TOOL_OPTIONS` |

## Taskfile

入口文件：`Taskfile.yml`。常用：

| Task | 作用 |
| --- | --- |
| `task up` / `down` / `logs` / `ps` | Compose 生命周期 |
| `task run` | 依赖 `up`，然后 `./mvnw -DskipTests spring-boot:run` |
| `task test` | `./mvnw -B test` |
| `task typecheck` | 前端 generate + typecheck + Storage token guard |
| `task frontend:dev` | Vite（`/api` 代理） |
| `task ci` | `test` + `typecheck` |

约束：

- Task **不**调用 SDKMAN；JDK 版本由你当前环境决定。
- `run` 使用 `spring-boot:run`（不要改成仅 `java -jar` 当本地默认，除非另有明确需求）。

## Docker Compose

文件：`docker-compose.yml`。

- 镜像：`postgres:16-alpine`
- DB / 用户 / 密码：`rosecloud_iam` / `rosecloud` / `rosecloud`（对齐 `application.yml`）
- 端口：`5432:5432`（本机已占用 5432 时需先停冲突实例或改映射）
- 时区：`TZ` / `PGTZ=Asia/Shanghai`

日常：`task up` 启动并 wait health；不用时 `task down`。

## SDKMAN（可选）

`.sdkmanrc` 仅声明 `java=25-tem`，方便在自己的 shell 里：

```bash
sdk env          # 按 .sdkmanrc 切换到 Java 25
# 然后再：
task run
```

- **Task 不会** `source` SDKMAN，也不会 `sdk env`。
- Maven 仍走 `./mvnw`，不要用 SDKMAN 管理本仓的 Maven。
- 若不使用 SDKMAN：自行安装 Temurin 25，并保证 `java -version` 为 25 后再跑 `task`。

## Quick start

```bash
# 可选：sdk env
task up
task run          # :8080
# 另开终端：
task frontend:dev
```

健康检查：`GET http://127.0.0.1:8080/actuator/health`。

I5 前端 console 默认跑在 `http://127.0.0.1:5173/`，同源访问 `/` 和 `/api`；Vite **不**再重写 `/api` 前缀，而是直接代理到 `http://localhost:8080/api/*`。

补充脚本：

- `cd frontend && npm run smoke:storage`：扫描 `src/`，发现 `localStorage` / `sessionStorage` 就失败
- `cd frontend && npm run test:e2e`：Playwright **壳层**冒烟（只校验 SPA 标题/导航）；需要先起 Vite，并设置 `E2E_BASE_URL`

注意：当前后端 Operator 与 User 共用 `rc_refresh` cookie 名。手工演示时，建议先跑完 Operator → Tenant，再清状态进入 User / Tenant flow，避免 cookie 语义混淆。明文 HTTP 联调前设置 `rosecloud.iam.cookies.secure=false`。

### I5 console smoke checklist（验收）

在 `task up` + `task run` + `task frontend:dev` 之后，用浏览器走完（邀请 token 从 `outbox_message` 拷贝即可）：

1. **Operator**：CLI 取 setup token → Setup begin/complete → Operator login（AccessToken 仅内存 Session 卡可见）
2. **Tenant**：Create tenant → 记下 outbox 里的 Owner invite token
3. **Owner**：Accept invite（begin/complete + TOTP）→ User login → Select tenant → Invite member（`MEMBER`）
4. **Member**：Clear / Log out → 用 Member invite 走 Accept → User login → Select tenant → Demo：`demo:read` = **200**，`demo:admin` = **403**
5. Owner/Admin 同页再调 Demo，两边均为 **200**；可选点 Refresh session / Log out

Storage 护栏：`npm run smoke:storage`（`task typecheck` 已含）。

## Operator setup CLI (I1)

一次性离线签发 setup token（成功打印到 stdout；**ACTIVE 后**再跑则非零退出；未完成 bootstrap 可重开）：

```bash
./mvnw -q -DskipTests spring-boot:run \
  -Dspring-boot.run.mainClass=io.rosecloud.iam.operator.OperatorSetupCli
```

然后 HTTP：`POST /api/operator/setup/begin` → `complete` → `login`；JWKS：`GET /api/.well-known/jwks.json`（见 OpenAPI）。

本机明文 HTTP 浏览器联调时，把 `rosecloud.iam.cookies.secure` 设为 `false`（默认 `true`）。

## Tenant owner invite (I2)

Operator 登录后可用 Bearer AccessToken 调：

```bash
curl -X POST http://127.0.0.1:8080/api/operator/tenants \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme","ownerEmail":"owner@example.com"}'
```

当前开发态邮件通过 `outbox_message` 可见；`tenant.owner_invited` payload 含一次性邀请 token，方便本地/测试取出。

Owner 接受邀请：

```bash
curl -X POST http://127.0.0.1:8080/api/invitations/accept/begin \
  -H "Content-Type: application/json" \
  -d '{"token":"<invite-token>","password":"owner invitation password"}'

curl -X POST http://127.0.0.1:8080/api/invitations/accept/complete \
  -H "Content-Type: application/json" \
  -d '{"token":"<invite-token>","totpCode":"123456"}'
```

## User sessions (I3)

Owner 接受邀请并激活后，可用邮箱 + 密码 + TOTP 走用户会话：

```bash
curl -i -X POST http://127.0.0.1:8080/api/sessions/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@example.com","password":"owner invitation password","totpCode":"123456"}'
```

响应体返回 UserContext AccessToken；响应头 `Set-Cookie` 写入 `rc_refresh`。之后：

```bash
curl http://127.0.0.1:8080/api/me/memberships \
  -H "Authorization: Bearer <user-access-token>"

curl -X POST http://127.0.0.1:8080/api/me/tenant-context \
  -H "Authorization: Bearer <user-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"membershipId":"<membership-id>"}'

curl -i -X POST http://127.0.0.1:8080/api/sessions/refresh \
  --cookie "rc_refresh=<refresh-token>"

curl -i -X POST http://127.0.0.1:8080/api/sessions/logout \
  --cookie "rc_refresh=<refresh-token>"
```

`/api/me/tenant-context` 返回 TenantContext AccessToken，并在 claims / body 中带展开后的权限码。`/api/sessions/refresh` 会轮换 refresh cookie；宽限期内并发重放旧 cookie 返回 `409 refresh_in_progress`，宽限期外旧 token 重放会撤销整个 session family。

## Tenant member invites and RBAC (I4)

拿到 TenantContext AccessToken 后，Owner / Admin 可邀请 `ADMIN` 或 `MEMBER`，开发态 token 仍从 `outbox_message` 取：

```bash
curl -X POST http://127.0.0.1:8080/api/tenants/<tenant-id>/invitations \
  -H "Authorization: Bearer <tenant-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"email":"member@example.com","roleCode":"MEMBER"}'
```

新用户沿用原来的 begin/complete：

```bash
curl -X POST http://127.0.0.1:8080/api/invitations/accept/begin \
  -H "Content-Type: application/json" \
  -d '{"token":"<invite-token>","password":"member invitation password"}'

curl -X POST http://127.0.0.1:8080/api/invitations/accept/complete \
  -H "Content-Type: application/json" \
  -d '{"token":"<invite-token>","totpCode":"123456"}'
```

已存在且 `ACTIVE` 的用户用 join 证明自己（不会重置 TOTP secret）：

```bash
curl -X POST http://127.0.0.1:8080/api/invitations/accept/join \
  -H "Content-Type: application/json" \
  -d '{"token":"<invite-token>","password":"existing user password","totpCode":"123456"}'
```

Tenant RBAC demo seam：

```bash
curl http://127.0.0.1:8080/api/demo/read \
  -H "Authorization: Bearer <tenant-access-token>"

curl http://127.0.0.1:8080/api/demo/admin \
  -H "Authorization: Bearer <tenant-access-token>"
```

`MEMBER` 只有 `demo:read`；`ADMIN` / `OWNER` 还能过 `demo:admin`。另外，TenantContext 访问下面接口会固定返回 `403`，用于明确“全局 TOTP 由更高权限边界处理”：

```bash
curl -X POST http://127.0.0.1:8080/api/tenants/<tenant-id>/users/<user-id>/totp/reset \
  -H "Authorization: Bearer <tenant-access-token>"
```
