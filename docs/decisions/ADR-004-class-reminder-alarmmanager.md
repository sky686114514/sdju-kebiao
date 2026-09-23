# ADR-004: 上课提醒采用 AlarmManager 精确闹钟为主、WorkManager 兜底降级

## Status
Accepted (2026-09-20)

## Background

PRD 场景 F 与成功指标对提醒有硬要求：「早八迟到次数 = 0」「提醒准点送达率 ≥ 95%」，且明确提到竞品被吐槽"害我好几次迟到"。因此**提醒的准时性是产品口碑的命门**，不能"差不多就行"。

候选方案与约束：

| 方案 | 精确性 | 权限/约束 | 适合"早八不能迟到" |
|------|--------|-----------|-------------------|
| `AlarmManager.setExactAndAllowWhileIdle` | 精确，Doze 下仍可触发（受 per-app 配额） | 需 `SCHEDULE_EXACT_ALARM`；Android 12+ 引入，**Android 14+ 对 targetSdk ≥33 的新装应用默认拒绝**；需 `canScheduleExactAlarms()` 检查 | **是** |
| `AlarmManager.setAlarmClock` | 最精确（系统不调整投递时间，必要时退出低功耗） | 会展现在系统闹钟图标上，语义为"闹钟"，资源消耗最高 | 备选 |
| `AlarmManager.setAndAllowWhileIdle` | 不精确（可被批量延迟） | 无需特殊权限 | 仅降级 |
| `WorkManager` 周期任务 | **不精确**（最小周期 15 分钟 + flex） | 无需特殊权限 | 仅兜底 |

`USE_EXACT_ALARM` 可作为替代权限（安装即授予、用户不可撤销），但**Google 政策限定为闹钟 / 日历类应用**；本 App 不上架、无需迁就该政策，且使用 `SCHEDULE_EXACT_ALARM` 语义更贴切（PRD 与 ARCHITECTURE 已一致采用该结论）。

本机实测补充：本机**无 Android 工具链、无设备**（ARCHITECTURE 第 2 章），因此"国产 ROM 后台清理下的真实送达率"**目前无实测样本**（见 OD-008），只能保证机制正确 + 可测量。

## Decision

**主路径**：`AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAtMillis, pendingIntent)`，在 `canScheduleExactAlarms()` 为真时使用。

**降级路径**：权限被拒时自动改用 `setAndAllowWhileIdle(...)`，同时在 App 内明确告知"提醒可能延迟"，并提供一键跳转系统"闹钟与提醒"设置的引导。

**兜底与自愈机制**：

1. `ReminderWorker`（WorkManager 2.11.2）每日运行一次，重排未来窗口的闹钟（应对重启后丢失、系统清理、权限变更）。
2. `BootReceiver`：`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → 触发重排。
3. `ExactAlarmPermissionReceiver`：监听 `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，权限恢复后立即重排。
4. **滚动窗口策略**：每次 App 打开 / 每日兜底 / 导入完成 / 学期切换时，重排**未来 7 天**的课次闹钟。窗口小 → 系统闹钟数量少（不怕配额）；窗口滚动 → 长期可靠。
5. **`requestCode` 唯一性**：`requestCode = hash(classSessionId, date)`，避免新闹钟覆盖旧闹钟（提醒类应用最常见的静默失效原因）。
6. `PendingIntent` 使用 `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`（API 31+ 强制 mutability）。
7. 提前量默认课前 15 分钟（PRD 开放问题 3），存 DataStore，变更后立即重排。
8. 每日早课汇总通知（可选）用 WorkManager 粗略时间，不占用精确闹钟配额。

**权限清单**：`POST_NOTIFICATIONS`（API 33+，运行时请求）、`SCHEDULE_EXACT_ALARM`（特殊权限，引导授予）、`RECEIVE_BOOT_COMPLETED`（静态声明）。

## Consequences

**正面**

- 早八场景的准时性有保障：`setExactAndAllowWhileIdle` 是"用户面向的精确时刻"场景的官方推荐 API。
- 权限被拒时不会静默失效，而是**降级且告知**，符合"诚实降级"（`schedule-exact-alarms` 官方文档明确要求 graceful degrade）。
- 三重自愈（日更 Worker + 开机广播 + 权限变更广播）覆盖了提醒类应用最常见的"重启后不响""系统清理后不响"两类失效。
- 滚动 7 天窗口使闹钟数量可控，既省电也规避 per-app 配额风险。

**负面**

- 精确闹钟本身耗电（系统难以批量调度），15 分钟提前量 × 若干节课会产生多次设备唤醒。缓解：窗口限制为 7 天、只在真正有课时段排闹钟。
- 用户未授予 `SCHEDULE_EXACT_ALARM` 时体验降级，需要清晰的引导文案（且 `POST_NOTIFICATIONS` 被拒时 App 内必须提示"提醒将无法送达"，对应 PRD 10 节"权限拒绝"边界）。
- **"≥95% 送达率"在当前环境无法验证**（无设备）。架构只保证机制与埋点（`remind_scheduled` / `remind_delivered` / `remind_missed`），数字待真机实测（OD-008）。

## Related ADRs
ADR-006（minSdk 26 与 targetSdk 36 决定精确闹钟的行为差异范围）、ADR-007（WorkManager 2.11.2 版本锚定）
