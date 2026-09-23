# ADR-008: 依赖注入采用手写轻量容器（不引入 Hilt / Dagger）

## Status
Accepted (2026-09-20)

## Background

PRD 要求"架构上预留手动编辑课程的扩展点"，且项目为单模块、单用户、约 30-40 个类的个人自用 App。依赖注入方案有两个方向：

| 候选 | 装配方式 | 编译期图校验 | 构建耦合 | 学习/调试成本 |
|------|----------|-------------|----------|--------------|
| **手写容器 `AppContainer`** | 构造器注入 + `by lazy` | 无（靠类型系统与代码评审） | 无 | 低 |
| Hilt（Dagger 之上，`androidx.hilt` 1.4.0） | 注解 + 生成代码 | 有（编译期报缺失绑定） | **高**：Hilt Gradle 插件自 Dagger 2.59 起要求 AGP 9 与 Gradle 9.1+；androidx.hilt 1.4.0 要求 KGP ≥ 2.2.0 | 高（注解语义、Component 层级、生成代码调试） |
| Koin（运行时 DI） | DSL 声明 | 无（运行时才报错） | 低 | 中（运行时错误不友好） |

关键权衡点：本项目的规模下，Hilt 的"编译期图校验"收益有限（依赖关系简单），但其**构建耦合成本**是实打实的——它会额外要求 AGP 9 + Gradle 9.1+（本项目已满足，但意味着**日后任何一方想回退版本都会被 Hilt 卡住**，而 ADR-007 已把"保守回退组合"列为风险预案，Hilt 会直接堵死这条退路）。

## Decision

**采用手写轻量容器 `AppContainer`，通过 Application 持有、ViewModel 经 `viewModelFactory { initializer { ... } }` 取依赖。不引入 Hilt / Dagger / Koin。**

```kotlin
// 示例，非指定：容器形状说明
class AppContainer(context: Context) {
    private val db: KebiaoDatabase by lazy { KebiaoDatabase.create(context) }
    val timeProvider: TimeProvider = SystemTimeProvider()
    val semesterRepository: SemesterRepository by lazy { SemesterRepository(db.semesterDao()) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(db.classSessionDao(), db.courseDao()) }
    val importMapper: ImportMapper by lazy { ImportMapper(WeekExpressionParser()) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context, timeProvider) }
    val widgetUpdater: WidgetUpdater by lazy { WidgetUpdater(context) }
}
```

**约束**：

1. ViewModel 构造函数**只接收接口**（`SemesterRepository` / `ScheduleRepository` / `TimeProvider` 等），不接收容器本身——这样单测可注入假实现，无需 DI 框架。
2. `AppContainer` 是**唯一**的装配点；业务代码中不得出现 `context.getSystemService(...)` 之类的隐式获取（容器负责提供）。
3. 依赖生命周期约定：`KebiaoDatabase` 单例（进程级）；Repository 无状态（进程级）；`TimeProvider` 进程级。

## Consequences

**正面**

- 构建链最短：不引入 Hilt Gradle 插件，**保住 ADR-007 的"保守回退组合"这条退路**，也避免注解处理器带来的编译时间与版本耦合。
- 调试直白：依赖在哪里被创建、用什么实现，一处可读；对"大一学生需要能自己看懂/改动这个工程"的场景很重要。
- 单测友好：ViewModel 依赖接口，测试直接传假 `TimeProvider`（`FixedTimeProvider`）与假 Repository，无需 Hilt 的 `@HiltAndroidTest` 基础设施。
- 符合"不过度设计"原则：依赖图简单时，DI 框架的收益低于其认知与构建成本。

**负面 / 风险**

- **无编译期依赖图校验**：漏装配一个依赖只会在运行时（或 IDE 报错）暴露。缓解：`AppContainer` 是单点，装配遗漏的排查面很小；且在 Application 启动路径上会有一次冒烟（冷启动即使用大部分依赖）。
- **规模增长后手工装配会变啰嗦**：若类数增长到 80+ 或出现多 Component 需求（例如按 Feature 隔离作用域），需要重新评估。
- 迁移成本：`AppContainer` 的接口化设计使日后迁移到 Hilt 可控（把 `by lazy` 换成 `@Provides`、把构造器注入保留即可）。

**触发重新评估的条件**（明确的可判定条件，避免"以后再优化"式模糊）：

1. 代码类数超过约 80 个；或
2. 出现需要作用域隔离（如"每 Feature 一个作用域"）的明确需求；或
3. 出现第二个数据源需要替换实现的场景。

## Related ADRs
ADR-007（不引入 Hilt 是为了保住版本回退退路）、ADR-002（Room 单例由容器提供）、ADR-001（`TimeProvider` 由容器注入，是可测试性的关键）
