# RoseCloud IAM

[![CI](https://github.com/zhijun-io/rosecloud-iam/actions/workflows/ci.yml/badge.svg)](https://github.com/zhijun-io/rosecloud-iam/actions/workflows/ci.yml)

RoseCloud 自用的 Workforce IAM：员工登录身份、多租户 Membership、内置 RBAC，以及独立的 PlatformOperator 运维面。绿地模块化单体（Java 25 + Spring Boot 4.1.0 + PostgreSQL；React + Vite）。

## Why

- 邀请制加入 Tenant；全局 User 凭据与 Tenant Membership 边界清晰
- 短寿 Access JWT + 轮换 Refresh Cookie；UserContext / TenantContext 分阶段签发
- 内置 Owner / Admin / Member + 代码声明 Permission；TOTP 归属全局 User
- PlatformOperator 与 User 会话隔离；离线 CLI 一次性 setup
- OpenAPI 生成 TypeScript 客户端，CI 防契约漂移

当前交付面是 **Plan 0001 薄切片**（I0 骨架已完成；I1–I6 见 Issues）。完整行为以规格为准，本 README 只服务开发者上手。

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
task up                 # Postgres
task run                # API :8080（spring-boot:run）
# 另开终端：
task frontend:dev       # Vite + /api 代理
```

健康检查：http://127.0.0.1:8080/actuator/health

| Task | 作用 |
| --- | --- |
| `task test` | 后端测试（Testcontainers） |
| `task typecheck` | OpenAPI 生成 + 前端 typecheck |
| `task ci` | 本地 CI 门禁 |
| `task down` | 停 Compose |

Compose 默认凭证与 `application.yml` 对齐：`rosecloud` / `rosecloud`，库名 `rosecloud_iam`，端口 `5432`。

## Documentation

| Doc | Purpose |
| --- | --- |
| [`CONTEXT.md`](CONTEXT.md) | 领域术语（Ubiquitous Language） |
| [`docs/spec/iam-v1.md`](docs/spec/iam-v1.md) | V1 行为规格 |
| [`docs/spec/prd-thin-slice.md`](docs/spec/prd-thin-slice.md) | 薄切片 PRD |
| [`docs/plans/0001-thin-slice.md`](docs/plans/0001-thin-slice.md) | I0–I6 增量计划 |
| [`docs/architecture.md`](docs/architecture.md) | 架构与模块边界 |
| [`docs/local-dev.md`](docs/local-dev.md) | 本地工具与时区 |
| [`docs/adr/`](docs/adr/) | 架构决策记录 |
| [`AGENTS.md`](AGENTS.md) | Agent 约定（Issues、Conventional Commits） |

Issues：[zhijun-io/rosecloud-iam](https://github.com/zhijun-io/rosecloud-iam/issues)（PRD #1，票 #2–#8）。

## Contributing

- 变更按 [`AGENTS.md`](AGENTS.md)：**Conventional Commits**（例如 `feat(session): …`、`docs: …`）
- Issue 标签与 tracker 约定见 [`docs/agents/`](docs/agents/)
- 尚未单独提供 `CONTRIBUTING.md` / `LICENSE`；以 Issues 与上述文档为准

## Maintainers

RoseCloud / [zhijun-io](https://github.com/zhijun-io)。问题优先开 GitHub Issue。
