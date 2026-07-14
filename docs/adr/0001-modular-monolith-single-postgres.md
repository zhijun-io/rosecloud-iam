# Modular monolith and single PostgreSQL

Status: accepted

RoseCloud IAM 是第一方内部平台能力，首版单实例、百万级以下身份规模。我们选择**单 Spring Boot 进程 + 单 PostgreSQL 权威库**的模块化单体：领域用包边界与 ArchUnit 约束，跨模块禁止直接碰 Entity/Repository，事务优先本地完成。

考虑过把 auth/system 继续拆成多服务或同仓多进程：会立刻引入分布式事务、会话一致性与运维面，收益与当前规模不匹配。也拒绝「无边界的大泥球分层」——没有模块约束时，User/Tenant/Session 会在数周内互相穿透。

后果：水平扩展靠日后拆分模块而非现在预支；用 Flyway、显式 `tenant_id` 与备份/PITR 承担可靠性，而不是多活应用层。
