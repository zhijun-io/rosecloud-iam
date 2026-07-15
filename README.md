# RoseCloud IAM

[![CI](https://github.com/zhijun-io/rosecloud-iam/actions/workflows/ci.yml/badge.svg)](https://github.com/zhijun-io/rosecloud-iam/actions/workflows/ci.yml)

RoseCloud 自用的 Workforce IAM：员工登录身份、多租户 Membership、内置 RBAC，以及独立的 PlatformOperator 运维面。绿地模块化单体（Java 25 + Spring Boot 4.1.0 + PostgreSQL；React + Vite）。

## Why

- 邀请制加入 Tenant；全局 User 凭据与 Tenant Membership 边界清晰
- 短寿 Access JWT + 轮换 Refresh Cookie；UserContext / TenantContext 分阶段签发
- 内置 Owner / Admin / Member + 代码声明 Permission；FactorBinding（TOTP 为首个 Factor）归属全局 Principal
- **MfaFeature** 默认关闭，可选 MFA；PlatformOperator 与 User 会话隔离；离线 CLI 一次性 setup
- OpenAPI 生成 TypeScript 客户端，CI 防契约漂移

当前交付面是 **Plan 0001 薄切片（已交付，I0–I6）**。完整行为以规格为准；切片验收对照 [`docs/acceptance/thin-slice-checklist.md`](docs/acceptance/thin-slice-checklist.md)。

## Stack

| Layer | Choice |
| --- | --- |
| Backend | Java 25, Spring Boot **4.1.0**, Maven Wrapper **3.9.16**, JPA, Flyway |
| Database | PostgreSQL 16 |
| Frontend | React 19, TypeScript, Vite 6, CSS Modules |
| Local DX | Task, Docker Compose；可选 `.sdkmanrc`（Task **不**调用 SDKMAN） |
| Timezone | `Asia/Shanghai`（JVM / Jackson / Hibernate / Compose / CI） |

## Prerequisites

- JDK **25** on `PATH` / `JAVA_HOME`（`task` 不会切 JDK）
- [Task](https://taskfile.dev/) 3.x
- Docker Compose
- Node **22** + npm

可选：SDKMAN，在仓库根执行 `sdk env`（见 `.sdkmanrc`）。

细节：[`docs/local-dev.md`](docs/local-dev.md)。

## Quick start

```bash
# 可选：sdk env
task up                 # Postgres + Mailpit
task run                # API :8080（spring-boot:run）
# 另开终端：
task frontend:dev       # Vite + /api 代理
```

Mailpit UI：http://127.0.0.1:8025/（SMTP 1025）

健康检查：http://127.0.0.1:8080/actuator/health

### Operator CLI（一次性 setup）

API 起来后另开终端签发 setup token（成功打印到 stdout；已 ACTIVE 再跑则非零退出）：

```bash
task operator:setup-token
# 等同于：
./mvnw -q -DskipTests spring-boot:run \
  -Dspring-boot.run.main-class=io.rosecloud.iam.operator.OperatorSetupCli
```

（Spring Boot 4 必须用 `main-class`；旧的 `mainClass` 会启动 Web 应用并与 `task run` 抢 8080。）

然后走 HTTP：`POST /api/operator/setup/begin` → `complete` → `login`（或前端 Console 的 Operator 流程）。

### 前端代理与 Cookie

- Vite：`http://127.0.0.1:5173/`，同源 `/api/*` **原样**代理到 `http://localhost:8080/api/*`（不 strip 前缀）
- AccessToken 仅内存；Refresh 仅为 `rc_refresh` Cookie（`Path=/api`，HttpOnly，SameSite=Lax）
- 明文 HTTP 浏览器联调：设 `rosecloud.iam.cookies.secure=false`（默认 `true`）
- Operator 与 User 共用 cookie 名；手工演示建议先完成 Operator→Tenant，再清会话进 User 流

### 邮件 / 邀请 token（Mailpit）

`task up` 会起 **Mailpit**（SMTP `1025`，UI `http://127.0.0.1:8025/`，容器 `TZ=Asia/Shanghai`）。应用默认 `rosecloud.iam.mail.enabled=true`，Outbox worker 每 ~2s 把 `tenant.owner_invited` / `tenant.member_invited` 发到 Mailpit；邮件正文含邀请 token 与 `#/accept-invite` 链接。

Mailpit 未起时邀请仍写入 `outbox_message`，发信会在日志 warn 后重试。也可继续用 SQL 读 Outbox（见 `docs/local-dev.md`）。

### 主路径任务

| Task | 作用 |
| --- | --- |
| `task test` | 后端测试（Testcontainers） |
| `task typecheck` | OpenAPI 生成 + **committed 客户端无 diff** + typecheck + Storage token guard |
| `task ci` | 本地 CI 门禁 |
| `task down` | 停 Compose |

额外前端脚本：

- `cd frontend && npm run smoke:storage`：断言源码里没有 Storage API 持久化 token
- `cd frontend && npm run test:e2e`：Playwright 壳层冒烟（需 Vite + `E2E_BASE_URL`）；完整 Operator→…→200/403 见 [`docs/local-dev.md`](docs/local-dev.md)

Compose 默认凭证与 `application.yml` 对齐：`rosecloud` / `rosecloud`，库名 `rosecloud_iam`，端口 `5432`。

## Documentation

| Doc | Purpose |
| --- | --- |
| [`CONTEXT.md`](CONTEXT.md) | 领域术语（Ubiquitous Language） |
| [`docs/spec/iam-v1.md`](docs/spec/iam-v1.md) | V1 行为规格 |
| [`docs/spec/prd-thin-slice.md`](docs/spec/prd-thin-slice.md) | 薄切片 PRD |
| [`docs/plans/0001-thin-slice.md`](docs/plans/0001-thin-slice.md) | I0–I6 增量计划（**已交付**） |
| [`docs/architecture.md`](docs/architecture.md) | 架构与模块边界 |
| [`docs/local-dev.md`](docs/local-dev.md) | 本地工具与时区 |
| [`docs/acceptance/thin-slice-checklist.md`](docs/acceptance/thin-slice-checklist.md) | 薄切片验收对照表 |
| [`docs/adr/`](docs/adr/) | 架构决策记录 |
| [`AGENTS.md`](AGENTS.md) | Agent 约定（Issues、Conventional Commits） |

Issues：[zhijun-io/rosecloud-iam](https://github.com/zhijun-io/rosecloud-iam/issues)（PRD #1，票 #2–#8）。

## Contributing

- 变更按 [`AGENTS.md`](AGENTS.md)：**Conventional Commits**（例如 `feat(session): …`、`docs: …`）
- Issue 标签与 tracker 约定见 [`docs/agents/`](docs/agents/)
- 尚未单独提供 `CONTRIBUTING.md` / `LICENSE`；以 Issues 与上述文档为准

## Maintainers

RoseCloud / [zhijun-io](https://github.com/zhijun-io)。问题优先开 GitHub Issue。
