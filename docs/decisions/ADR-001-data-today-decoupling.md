# ADR-001: 数据与"今天"解耦——采用 Semester / Course / ClassSession 三层实体 + SessionOccurrence 计算产物

## Status
Accepted (2026-09-20)

## Background

本产品最容易做错的地方不是 UI，而是"今天上什么课"这个问题的回答方式。

课表数据是**静态的学期数据**（一学期导入一次），而"今天该上什么课"是**随日期变化的计算结果**。如果实现时把"今天"混进数据层——例如导入时就把课算好写进表、或者存一个 `is_today` 标记、或者让 UI 自己用 `LocalDate.now()` 去过滤——那么以下 PRD 验收标准会全部失效或埋下静默错误：

- PRD 9.3：「切换学期后当天课程 3 秒内刷新，旧课残留 = 0」
- PRD 9.1：「单周仅单周课、双周仅双周课出现在当天课程」
- PRD 9.1：「大学物理B(1) 第 2 周 E教305、第 3 周起 B105」
- PRD 9.7：「内容更新有过渡动画，不瞬时闪烁」
- PRD 11：「冷启动到看到今日课程 < 2s」（不允许每次启动做联网/重算密集操作）

同时，真实数据（PRD 4.2 节）有三条硬约束：

1. 同一门课不同周换教室、换教师 —— **不是一行能表达的关系**；
2. 周次是**不连续 / 混合集合**（`2,3-4,5,8,6-7`），且带**单双周**（`2-16 双周`、`3-15 单周`）；
3. 存在**纯线上课**与**无固定时间地点**的课（军事技能），不能硬塞进网格。

## Decision

**采用四层概念，其中前三层落库、第四层永不落库：**

| 层 | 概念 | 落库 | 语义 |
|----|------|------|------|
| L1 | `Semester` | 是 | 学期；含 `startDate`（第 1 周周一）与 `totalWeeks` |
| L2 | `Course` | 是 | 逻辑课程（"是什么课"） |
| L3 | `ClassSession` | 是 | 一条 = 一组「周次集合 + 星期 + 节次 + 地点 + 教师」；同门课的换教室/换教师 = 多条互斥周次的 L3 |
| L4 | `SessionOccurrence` | **否** | 某一天命中 L3 之后的课次，**每次求值现算，不持久化** |

配套四条强制约定：

1. **L3 必须存两份周次表示**：`weeks_raw`（原始串，审计回溯）与 `week_numbers`（规范化集合，求值用）。
2. **`ScheduleCalculator.occurrencesOn(date, semester, sessions, now)` 是纯函数**，`date` 与 `now` 由外部注入，函数体内**禁止**调用 `LocalDate.now()` / `System.currentTimeMillis()`。
3. **"今天是第几周"抽成 `Semester.weekOf(date): Int?` 纯函数**，输入只有 `startDate` / `totalWeeks` / `date`。
4. **UI 不自己算"今天"**：`UI` 只订阅 `Flow<TodaySchedule>`；学期切换、跨零点、数据变更全部经 `Flow` 重发触发重算。

时间源经 `TimeProvider` 接口注入（`SystemTimeProvider` / `FixedTimeProvider`）。

## Consequences

**正面**

- **可测试**：`ScheduleCalculator` 与 `WeekExpressionParser` 是纯 JVM 函数，可用 `FixedTimeProvider` 断言"2026-09-28（第 3 周周一）的课"，无需 mock 系统时间，也不依赖设备。
- **换学期刷新天然成立**：`is_current` 变更 → `Flow` 重发 → 今日课程必然重算，不存在"忘了刷新"的代码路径（PRD 9.3 的核心机制）。
- **单双周 / 换教室正确性可证明**：L3 的互斥周次建模使"第 2 周 E教305、第 3 周 B105"成为两个独立行，求值只是集合包含判断，没有隐式优先级。
- **线上课 / 无时间课不会被网格吞掉**：L3 允许 `weekday = null` / `startTime = null`，UI 侧可独立分区渲染（PRD 9.6）。
- **离线可用**：计算全在本地，无网络依赖（PRD 11）。

**负面**

- 表结构与映射代码比"一行一课程"多；导入映射需把教务表格的合并单元格拆成多条 L3（`ImportMapper` 承担此复杂度）。
- "同门课多条 L3"对新手不直观，必须靠术语表（ARCHITECTURE 6.1）与测试夹具约束，否则后续维护者可能退回"一行一课程"。

**已识别的风险与对策**

- 风险：`SessionOccurrence` 若被误持久化（例如为性能缓存），会重新引入"数据与今天耦合"。对策：`OccurrenceEntity` 不存在于 schema，评审时检查 `class_sessions` 表不得含日期字段。
- 风险：`Semester.startDate` 取值错误会让全学期偏移一周（PRD 5.3 提到竞品 WakeUp 曾识别错开学周）。对策：见 OD-004 与"学期起始日校准"步骤。

## Related ADRs
ADR-002（本地存储选 Room，承载以上三级实体）、ADR-003（导入需产出上述分层结构）、ADR-006（minSdk 26 使 `java.time` 原生可用，是 `weekOf` 纯函数的前提）
