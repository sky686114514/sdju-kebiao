# ADR-005: 桌面小组件采用 Glance（而非传统 RemoteViews）

## Status
Accepted (2026-09-20)

## Background

PRD 要求桌面小组件展示"当天课程"（PRD 9.8、功能 6.2 的 P1 项）。小组件要展示的数据形状与主界面的"今日课程"**完全一致**——同一份 `List<SessionOccurrence>`（课程名 + 时间 + 地点）。

候选方案：

| 候选 | 写法 | 最低 API | 与主 UI 的一致性 | 结论 |
|------|------|----------|-----------------|------|
| **Glance AppWidget 1.2.0**（2026-08-26 稳定） | Compose 风格 API | Glance 1.x 支持到 API 23 量级，本项目 minSdk 26 满足 | 与主 UI 同源，可复用主题 token 与领域模型 | **选定** |
| 传统 `RemoteViews` | XML 布局 + 手工拼装/更新 | 全版本 | 需维护第二套布局与状态同步，易与主 UI 分裂 | 备选 |
| Glance 1.3.0-alpha02 | 同上 | 需 AGP ≥9.2.0、compileSdk 37 | — | 不采用（Alpha） |

## Decision

**采用 Glance `androidx.glance:glance-appwidget:1.2.0`。**

**刷新时机**（对应 PRD 9.8「换学期或改课后能刷新」）：

```text
WidgetUpdater 触发 KebiaoWidget().updateAll(context) 的时机：
  1. 课表数据写入成功（Room 写入后 Repository 发事件）
  2. 学期切换（is_current 变更）
  3. 跨零点（复用 TimeProvider 的日期变更信号）
  4. WorkManager 周期性刷新（兜底）
  5. App 冷启动 / 回到前台
```

**内容与尺寸**：日期 + 周次（"第 3 周 周一"）+ 今日课次列表（时间 + 课程名 + 地点），或"今天没有课"。先做 **4x2（全天课程）**，1x1（仅下一节课）作为 P1（PRD 开放问题 2）。

**必须写进实现约束的 Glance 限制**（避免乐观预期导致返工）：

1. Glance 运行在独立的 `RemoteViews` 渲染路径上，**不支持任意 Compose 组件**（复杂动画、任意 `Modifier` 不可用），只能用其提供的基础组件子集。因此**小组件不具备主界面同等的过渡动画能力**，与 ARCHITECTURE 10.2 的动画预算**不是同一套规则**，不适用"小组件也要有过渡动画"的期待。
2. 小组件刷新**不能依赖用户页面交互**，必须由数据变化或系统调度触发（见上）。
3. Glance 的预览与布局需通过 `GlanceAppWidget` / `GlanceAppWidgetReceiver` 声明并在 `res/xml` 注册 `appwidget-provider` 信息；`updatePeriodMillis` 最小 30 分钟且不可靠，**不作为主刷新机制**（这正是需要显式 `WidgetUpdater` 的原因）。

## Consequences

**正面**

- **一套 UI 心智模型**：Glance 用 Compose 风格 API，可与主 UI 共享主题 token 与"取今日课次"的领域逻辑，避免 RemoteViews 下"两套布局 + 两处更新时机 + 两种渲染差异"的长期维护成本。
- RemoteViews 对**列表类**内容的表达能力弱（需手工构造集合视图），而小组件的核心内容恰是"今日课次列表"，Glance 明显更合适。
- minSdk 26 满足 Glance 的最低要求，无需为小组件抬高 minSdk。

**负面**

- 引入 Glance 依赖（及其对 Compose runtime / DataStore 的传递依赖），APK 体积略增（个人项目可接受）。
- Glance 的 API 表面比 RemoteViews 小，遇到其不支持的样式时只能妥协设计（已在上文明确）。
- 小组件刷新有系统调度延迟，无法做到"改课立即可见"的绝对即时（`updateAll` 会尽快生效，但仍受系统约束）；因此 PRD 9.8 的验收应表述为"返回桌面后随数据更新"，而非"毫秒级同步"。

**版本风险**：Glance 1.2.0 是 2026-08-26 发布的新稳定版，样本较少；若构建期遇到阻塞，回退方案为传统 `RemoteViews`（代价是第二套 UI 代码）。

## Related ADRs
ADR-001（小组件复用 `SessionOccurrence`）、ADR-007（Glance 1.2.0 与 compileSdk 37 / AGP 9.3.3 的兼容性锚定）
