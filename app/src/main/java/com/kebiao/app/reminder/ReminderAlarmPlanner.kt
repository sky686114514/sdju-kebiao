package com.kebiao.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import java.time.LocalDate

/**
 * AlarmManager 交互的唯一出口 —— 把"系统闹钟 API 的坑"集中到一处（ADR-004）。
 *
 * 集中在这里的三件事，任何一处写错都会让提醒静默失效：
 *  1. **请求码稳定性**：请求码 = 稳定的 (sessionId, 日期, 是否稍后提醒) 哈希。
 *     若换成会随时间变化的值，`FLAG_UPDATE_CURRENT` 就定位不到旧 PendingIntent，
 *     从而留下重复闹钟、同一节课被通知两次。
 *  2. **显式降级**：`setExactAndAllowWhileIdle` 需要 `SCHEDULE_EXACT_ALARM`，Android 14+ 默认拒绝；
 *     未获授权时降级为 `setAndAllowWhileIdle`（Doze 下仍能唤醒，只是时间不精确）。
 *  3. **取消用 FLAG_NO_CREATE**：只取已存在的 PendingIntent，不因为"取消"凭空创建一个新的。
 */
internal class ReminderAlarmPlanner(
    private val context: Context,
    private val alarmManager: AlarmManager,
) {

    /** Android 12 以下没有"精确闹钟授权"概念，恒为允许。 */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    /** 派发一个上课提醒闹钟；[exact] 为 false 时走不精确降级路径。 */
    fun schedule(
        sessionId: Long,
        date: LocalDate,
        triggerAtMillis: Long,
        courseName: String,
        timeText: String,
        locationText: String,
        exact: Boolean,
        isSnooze: Boolean,
    ) {
        val pending = pendingIntent(
            sessionId = sessionId,
            date = date,
            courseName = courseName,
            timeText = timeText,
            locationText = locationText,
            isSnooze = isSnooze,
            create = true,
        ) ?: throw IllegalStateException("创建提醒 PendingIntent 失败，无法排定闹钟")

        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    /** 取消一个已排的提醒；不存在时静默返回（清场时属于正常情况）。 */
    fun cancel(sessionId: Long, date: LocalDate, isSnooze: Boolean) {
        val pending = pendingIntent(sessionId, date, "", "", "", isSnooze, create = false) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(
        sessionId: Long,
        date: LocalDate,
        courseName: String,
        timeText: String,
        locationText: String,
        isSnooze: Boolean,
        create: Boolean,
    ): PendingIntent? {
        val flags = if (create) {
            // API 31+ 强制显式 mutability；用 IMMUTABLE（我们不修改 Intent 内容）。
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeOf(sessionId, date, isSnooze),
            ReminderAlarmReceiver.buildIntent(
                context = context,
                sessionId = sessionId,
                dateIso = date.toString(),
                courseName = courseName,
                timeText = timeText,
                locationText = locationText,
                isSnooze = isSnooze,
            ),
            flags,
        )
    }

    /** 请求码必须只由稳定输入决定（见类 KDoc 第 1 点）。 */
    private fun requestCodeOf(sessionId: Long, date: LocalDate, isSnooze: Boolean): Int {
        var result = sessionId.hashCode()
        result = 31 * result + date.toEpochDay().hashCode()
        result = 31 * result + if (isSnooze) 1 else 0
        return result and REQUEST_CODE_MASK
    }

    private companion object {
        /** 24 位掩码：AlarmManager 的请求码是 Int，数十个闹钟规模下碰撞概率可忽略。 */
        const val REQUEST_CODE_MASK = 0x00FFFFFF
    }
}
