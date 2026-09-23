package com.kebiao.app.di

import android.content.Context
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.core.onFailure
import com.kebiao.app.reminder.NotificationChannels
import com.kebiao.app.reminder.ReminderWorker
import com.kebiao.app.widget.EmptyAccentPaletteProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 启动自检与装配（由 [AppContainer.bootstrap] 调用一次）。
 *
 * 五项启动动作，逐项说明"为什么必须在启动时做"：
 *
 * 1. **建通知渠道**：渠道必须在发通知前存在，否则 Android 8.0+ 的通知会被直接丢弃。
 * 2. **注册每日兜底 Worker**：`KEEP` 策略，重复调用安全。
 * 3. **推断当前学期**：`applyInferredCurrent` 只在"0 个或多个 current"时才动手，
 *    因此不会覆盖用户在假期里的手动切换（ARCHITECTURE.md 第 6.6 节）。
 * 4. **重排提醒**：App 每次启动都重排 7 天滚动窗口，这是提醒可靠性的主要来源之一。
 * 5. **刷新小组件**：跨零点、换学期、改课之后，小组件可能在用户看到 App 之前就已过时。
 *
 * 全部动作**不阻塞主线程**：跑在 [AppContainer.applicationScope] 上，
 * 且每个动作失败只写事件日志，不让启动链路崩掉（App 必须能打开）。
 */
class AppBootstrap(
    private val container: AppContainer,
    private val context: Context,
) {

    fun run(scope: CoroutineScope) {
        scope.launch {
            ensureNotificationChannels()
            registerDailyReschedule()
            inferCurrentSemester()
            rescheduleReminders()
            refreshWidgets()
            warnIfAccentPaletteMissing()
        }
    }

    private fun ensureNotificationChannels() {
        runCatching { NotificationChannels.ensure(context) }
            .onFailure { record("notification_channels_failed", it) }
    }

    private fun registerDailyReschedule() {
        runCatching { ReminderWorker.enqueuePeriodic(context) }
            .onFailure { record("enqueue_periodic_work_failed", it) }
    }

    private suspend fun inferCurrentSemester() {
        val today = container.timeProvider.today()
        container.semesterRepository.applyInferredCurrent(today)
            .onFailure { record("infer_current_semester_failed", it.cause) }
    }

    private suspend fun rescheduleReminders() {
        runCatching { container.reminderScheduler.reschedule() }
            .onFailure { record("reminder_reschedule_failed", it) }
    }

    private suspend fun refreshWidgets() {
        container.widgetUpdater.refresh()
            .onFailure { record("widget_refresh_failed", it.cause) }
    }

    /**
     * 课程识别色板未接线时显式留痕。
     *
     * 小组件会退化为"没有识别环的纯文本"，功能不受影响，但**设计规则被绕过**，
     * 因此必须能通过本地事件日志发现，而不是靠肉眼偶然注意到。
     */
    private fun warnIfAccentPaletteMissing() {
        if (container.accentPaletteProvider is EmptyAccentPaletteProvider) {
            container.localEventLog.append(
                LocalEvent(
                    name = LocalEvents.ERROR_OCCURRED,
                    timestampMillis = System.currentTimeMillis(),
                    attributes = mapOf(
                        "kind" to "widget_accent_palette_missing",
                        "impact" to "小组件不显示课程识别环（颜色仍全部走 Token，无硬编码）",
                    ),
                )
            )
        }
    }

    private fun record(kind: String, cause: Throwable?) {
        container.localEventLog.append(
            LocalEvent(
                name = LocalEvents.ERROR_OCCURRED,
                timestampMillis = System.currentTimeMillis(),
                attributes = mapOf(
                    "kind" to kind,
                    "cause" to (cause?.let { it::class.simpleName } ?: "unknown"),
                ),
            )
        )
    }
}

