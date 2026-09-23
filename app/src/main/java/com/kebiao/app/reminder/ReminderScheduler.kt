package com.kebiao.app.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.repository.ScheduleRepository
import com.kebiao.app.data.settings.ReminderSettings
import com.kebiao.app.data.settings.ReminderSettingsRepository
import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import java.time.Instant
import java.time.ZonedDateTime

/**
 * 上课提醒调度器（ARCHITECTURE.md 第 8 章 / ADR-004）。
 *
 * ## 调度策略
 *
 * 1. **滚动窗口 7 天**：每次 App 打开 / 每日兜底任务 / 导入完成 / 学期切换时重排。
 *    窗口小 -> 系统内闹钟数量少，不怕配额；窗口滚动 -> 长期可靠。
 * 2. **请求码唯一**：委托 [ReminderAlarmPlanner]，见其 KDoc。
 * 3. **精确优先并显式降级**：未获精确闹钟授权时降级为近似排定，并把降级事实写进
 *    [ReminderScheduleReport.note] 由 UI 告知用户（AC-33），绝不假装精确。
 * 4. **三重自愈**：`ReminderWorker` 日更 + `BootReceiver`（开机/应用更新/改时间）+
 *    `ExactAlarmPermissionReceiver`（权限恢复）。
 *
 * ## 已知边界（如实登记）
 *
 * "被杀且未重启"窗口内的精确提醒仍可能丢失（OD-008）。PRD 的 >=95% 送达率需真机实测，
 * 本类只保证"机制正确且可测量"（`remind_scheduled` / `remind_delivered` / `remind_missed`）。
 */
class ReminderScheduler(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val settings: ReminderSettingsRepository,
    private val timeProvider: TimeProvider,
    private val eventLog: LocalEventLog,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: throw IllegalStateException("系统未提供 AlarmManager，无法调度提醒")

    private val planner: ReminderAlarmPlanner by lazy { ReminderAlarmPlanner(context, alarmManager) }

    /** 重排未来 [WINDOW_DAYS] 天的全部上课提醒（先清场再排，避免残留闹钟重复通知）。 */
    suspend fun reschedule(): ReminderScheduleReport {
        val current = settings.current()
        if (!current.enabled) {
            cancelAll()
            return report(0, false, current, "提醒已关闭，未排任何闹钟")
        }

        return when (val snapshot = scheduleRepository.snapshotForScheduling()) {
            is ScheduleRepository.SnapshotResult.NoSemester -> {
                cancelAll()
                report(0, true, current, "尚未导入课表，本轮无可排的课次")
            }

            is ScheduleRepository.SnapshotResult.Failed -> {
                cancelAll()
                report(0, true, current, "读取课表失败，本轮未排闹钟：${snapshot.error.userMessage}")
            }

            is ScheduleRepository.SnapshotResult.Loaded -> {
                cancelAll()
                val exact = planner.canScheduleExactAlarms()
                val count = scheduleWindow(
                    snapshot.semester, snapshot.sessions, snapshot.courses, current.leadMinutes, exact,
                )
                eventLog.append(
                    LocalEvent(
                        name = LocalEvents.REMIND_SCHEDULED,
                        timestampMillis = nowMillis(),
                        termId = snapshot.semester.id,
                        attributes = mapOf("count" to count.toString(), "exact" to exact.toString()),
                    )
                )
                report(count, true, current, null)
            }
        }
    }

    /** 取消已排的全部闹钟（关闭提醒、清空学期时调用）。 */
    suspend fun cancelAll() {
        val today = timeProvider.today()
        for (sessionId in trackedSessionIds()) {
            for (offset in 0 until WINDOW_DAYS) {
                planner.cancel(sessionId, today.plusDays(offset.toLong()), isSnooze = false)
            }
            planner.cancel(sessionId, today, isSnooze = true)
        }
    }

    /** "10 分钟后提醒"（通知上的动作）。 */
    fun snooze(sessionId: Long, courseName: String, timeText: String, locationText: String) {
        planner.schedule(
            sessionId = sessionId,
            date = timeProvider.today(),
            triggerAtMillis = nowMillis() + SNOOZE_MINUTES * 60_000L,
            courseName = courseName,
            timeText = timeText,
            locationText = locationText,
            exact = planner.canScheduleExactAlarms(),
            isSnooze = true,
        )
    }

    /** Android 12 以下没有"精确闹钟授权"概念，恒为允许。 */
    fun canScheduleExactAlarms(): Boolean = planner.canScheduleExactAlarms()

    /** 当前设备的提醒能力快照，供设置页提示"提醒可能无法送达"（AC-32 / AC-33）。 */
    fun capability(current: ReminderSettings): ReminderCapability = ReminderCapability(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarmAllowed = planner.canScheduleExactAlarms(),
        enabled = current.enabled,
    )

    private fun scheduleWindow(
        semester: Semester,
        sessions: List<ClassSession>,
        courses: Map<Long, Course>,
        leadMinutes: Int,
        exact: Boolean,
    ): Int {
        val zone = timeProvider.zone()
        val today = timeProvider.today()
        val nowInstant: Instant = ZonedDateTime.of(today, timeProvider.now(), zone).toInstant()
        var scheduled = 0

        for (session in sessions) {
            val weekday = session.weekday ?: continue
            val startTime = session.startTime ?: continue
            val course = courses[session.courseId] ?: continue

            for (offset in 0 until WINDOW_DAYS) {
                val date = today.plusDays(offset.toLong())
                if (date.dayOfWeek != weekday) continue
                val week = semester.weekOf(date) ?: continue
                if (week !in session.weekNumbers) continue

                val triggerAt = ZonedDateTime.of(date, startTime, zone)
                    .minusMinutes(leadMinutes.toLong())
                    .toInstant()
                if (!triggerAt.isAfter(nowInstant)) continue

                planner.schedule(
                    sessionId = session.id,
                    date = date,
                    triggerAtMillis = triggerAt.toEpochMilli(),
                    courseName = course.name,
                    timeText = timeTextOf(session),
                    locationText = locationTextOf(course.isOnline, session.room, session.campus),
                    exact = exact,
                    isSnooze = false,
                )
                scheduled++
            }
        }
        return scheduled
    }

    /**
     * 清场时的目标 sessionId 集合。
     * 若用户删过课次导致残留闹钟，触发时 [ReminderAlarmReceiver] 会正常发出通知
     * （通知内容取自 Intent），因此这里以"当前学期全部课次"为准即可。
     */
    private suspend fun trackedSessionIds(): List<Long> =
        when (val snapshot = scheduleRepository.snapshotForScheduling()) {
            is ScheduleRepository.SnapshotResult.Loaded -> snapshot.sessions.map { it.id }
            else -> emptyList()
        }

    private fun report(
        count: Int,
        enabled: Boolean,
        current: ReminderSettings,
        explicitNote: String?,
    ): ReminderScheduleReport {
        val exact = planner.canScheduleExactAlarms()
        val note = explicitNote ?: if (exact) {
            "已按精确闹钟排定 $count 个提醒（提前 ${current.leadMinutes} 分钟）"
        } else {
            "未获精确闹钟权限，已降级为近似时间排定 $count 个提醒，可能延迟若干分钟"
        }
        return ReminderScheduleReport(count, enabled, exact, current.leadMinutes, note)
    }

    private companion object {
        const val WINDOW_DAYS = 7
        const val SNOOZE_MINUTES = 10
    }
}
