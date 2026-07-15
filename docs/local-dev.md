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
| `task typecheck` | 前端 generate + typecheck |
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
