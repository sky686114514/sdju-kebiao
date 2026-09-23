package com.kebiao.app.reminder

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kebiao.app.R
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.di.AppContainer
import com.kebiao.app.domain.model.TodayEmptyReason
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * 提醒兜底 Worker（ARCHITECTURE.md 第 8.2 节）。
 *
 * 两个职责：
 *  1. **重排**：覆盖"被系统/用户杀掉后重启""权限刚被授予""系统清理了闹钟"三类失效；
 *  2. 可选的**每日早课汇总通知**（设置开关控制，默认关闭）。
 *
 * 为什么用 WorkManager 而不是常驻 AlarmManager：兜底任务只需"大致每天跑一次"，
 * 不需要精确；把精确闹钟的配额留给真正的上课提醒。
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.from(applicationContext)
        return try {
            val report = container.reminderScheduler.reschedule()
            if (report.enabled) {
                maybePostDailySummary(container)
            }
            Result.success()
        } catch (e: Exception) {
            // 兜底任务自身失败必须重试，否则"自愈"这条链就断了；同时留痕，不静默。
            container.localEventLog.append(
                LocalEvent(
                    name = LocalEvents.ERROR_OCCURRED,
                    timestampMillis = System.currentTimeMillis(),
                    attributes = mapOf(
                        "kind" to "reminder_worker_failed",
                        "cause" to (e::class.simpleName ?: "unknown"),
                    ),
                )
            )
            Result.retry()
        }
    }

    private suspend fun maybePostDailySummary(container: AppContainer) {
        val settings = container.reminderSettingsRepository.current()
        if (!settings.dailySummaryEnabled) return
        if (container.timeProvider.now().hour !in SUMMARY_HOUR_RANGE) return

        val today = try {
            container.scheduleRepository.observeToday().first()
        } catch (e: Exception) {
            container.localEventLog.append(
                LocalEvent(
                    name = LocalEvents.ERROR_OCCURRED,
                    timestampMillis = System.currentTimeMillis(),
                    attributes = mapOf("kind" to "daily_summary_read_failed"),
                )
            )
            return
        }

        val (title, body) = buildSummaryContent(today.items.map { item ->
            listOfNotNull(item.startTime?.toString(), item.courseName, item.room).joinToString(" · ")
        }, today.emptyReason)

        NotificationChannels.ensure(applicationContext)
        NotificationManagerCompat.from(applicationContext).notify(
            DAILY_SUMMARY_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, NotificationChannels.DAILY_SUMMARY)
                .setSmallIcon(R.drawable.kebiao_ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** 汇总文案：三种"没有课"分别给不同文案，禁止统一写"暂无数据"（Spec 第 7 节）。 */
    private fun buildSummaryContent(lines: List<String>, reason: TodayEmptyReason?): Pair<String, String> {
        if (lines.isNotEmpty()) {
            return applicationContext.getString(R.string.kebiao_summary_title) to lines.joinToString("\n")
        }
        return when (reason) {
            TodayEmptyReason.OUTSIDE_SEMESTER ->
                applicationContext.getString(R.string.kebiao_summary_title) to
                    applicationContext.getString(R.string.kebiao_summary_outside_semester)

            TodayEmptyReason.NOT_IMPORTED ->
                applicationContext.getString(R.string.kebiao_summary_title) to
                    applicationContext.getString(R.string.kebiao_summary_not_imported)

            else ->
                applicationContext.getString(R.string.kebiao_summary_title) to
                    applicationContext.getString(R.string.kebiao_summary_no_class)
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC = "kebiao_reminder_daily"
        private const val UNIQUE_ONE_SHOT = "kebiao_reminder_reschedule"
        private const val DAILY_SUMMARY_NOTIFICATION_ID = 0x1101

        /** 汇总只在早晨窗口发（避免下午重试时弹一条过时的"今天的课"）。 */
        private val SUMMARY_HOUR_RANGE = 6..10

        /** 每天一次的自愈重排（WorkManager 最小周期 15 分钟，这里取 1 天 + 1 小时弹性）。 */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                repeatInterval = Duration.ofDays(1),
                flexTimeInterval = Duration.ofHours(1),
            ).setConstraints(Constraints.NONE).build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** 事件驱动的兜底重排（开机、应用更新、改时间、精确闹钟权限恢复）。 */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
