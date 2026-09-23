# ADR-002: 本地存储采用 Room（而非 DataStore / SQLDelight）

## Status
Accepted (2026-09-20)

## Background

本项目需要在本地存储并**查询**以下结构：学期 → 课程 → 上课安排（周次集合、星期、节次、地点、教师），并在"某一天"上做**关系型过滤**（`weekday == 今天` 且 `本周 ∈ weekNumbers`）。

关键特征是**关系型查询 + 多表关联 + 数据量小**（PRD 载明单学期约 14 门课 + 2 门备注课；拆成 L3 后约数十行）。

候选方案：

| 候选 | 类型 | 关系型查询 | 迁移能力 | 生态 |
|------|------|-----------|---------|------|
| **Room 2.8.5** | SQLite 之上的 ORM/DAO（Android 官方） | 强（SQL + 外键 + 索引 + `@Transaction`） | 显式 `Migration`，schema JSON 可版本化 | Jetpack 一等公民，KSP 处理，与 Compose/Lifecycle 无缝 |
| DataStore 1.2.1 | 键值 / 类型化对象持久化（Proto 或 Preferences） | **无**（无查询、无关联、无索引） | Proto 需自行处理 | 适合标量设置，不适合列表 + 过滤 |
| SQLDelight | SQL-first 代码生成（跨平台） | 强 | `.sqm` 迁移文件 | 强，但主要为 KMP 场景；本项目仅 Android |

`mvp-stack.md` 将 SQLite 归为"嵌入式 / 桌面端，规模上限百万级"——本项目的量级（单学期数百行）远在能力之内，REST/后端数据库方案无必要（且 PRD 明确无后端）。

## Decision

**采用 Room 2.8.5（KSP 处理，`exportSchema = true`）。**

- 不使用 DataStore 承载课表数据。DataStore 仅用于**标量设置**（提醒提前量、主题偏好、是否已引导过权限），与 Room 分工明确：**有查询需求 → Room；纯标量设置 → DataStore。**
- 不使用 SQLDelight：本项目单 Android 平台，SQLDelight 的跨平台收益为零，而 Room 的 Android 生态（Compose/Lifecycle/Test）集成成本更低。
- **不使用 Room 3.0（`androidx.room3`）**：截至 2026-09 仍为 Alpha（KMP 导向、KSP-only、移除 Java 代码生成），不用于稳定交付。
- 迁移策略：显式 `Migration`，**禁用 `fallbackToDestructiveMigration`**（个人项目里丢课表比崩溃更痛，且会破坏"离线可用"承诺）。

查询策略（对应 ARCHITECTURE 6.3）：

- 建索引：`courses(semester_id)`、`class_sessions(course_id)`、`class_sessions(weekday)`。
- **不为 `week_numbers` 建索引**：单学期数据量在百行量级，"某天有哪些课"用一次 `SELECT` 取该学期全部 L3 + 内存过滤（O(n)，n 极小）。这是"避免过早优化"原则的直接应用；若未来支持整届课表再评估 `LIKE` 索引或位图列。

## Consequences

**正面**

- 关系型查询表达能力完全覆盖需求：`@Transaction` 保证"切学期"与"整批导入"的原子性（PRD 9.1「不产生半截数据」由 DB 事务直接保证）。
- `Flow<T>` 返回型 DAO 使"表变更 → UI 刷新"自动成立，是实现 ADR-001"数据与今天解耦"刷新链路的载体。
- `exportSchema` + 显式迁移，使 schema 演进可审计（符合 `spec-as-contract.md` 的"活规格 / 决策留痕"）。
- KSP2 编译期处理，无反射（对启动耗时友好，服务 PRD 11「冷启动 < 2s」）。

**负面**

- 引入 Room + KSP 两个构建期组件，首次构建耗时增加（一次性）。
- 需要维护 Entity ↔ 领域模型的映射代码（`data/repository/mapper`），这是分层带来的必要成本。
- Room 的 `@Index` 不支持部分索引（`WHERE` 子句），因此"至多一个 `is_current = 1`"的不变式必须由**事务 + 单点写入口 + 单测**保证，而非 DB 约束（已在 ARCHITECTURE 6.6 记明）。

## Related ADRs
ADR-001（Room 承载三层实体）、ADR-006（minSdk 26）、ADR-007（Room 2.8.5 与 KSP 2.3.11 的版本锚定）
