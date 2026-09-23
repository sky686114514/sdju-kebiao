package com.kebiao.app.reminder

import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.schedule.SemesterFormatting

/**
 * 提醒能力快照（设置页据此提示"提醒可能无法送达"，AC-32）。
 *
 * 三个维度刻意分开而不是合成一个布尔：用户需要知道"到底是通知被关了、还是精确闹钟没授权、
 * 还是自己把提醒关了"，三者的修复路径完全不同。
 */
data class ReminderCapability(
    val notificationsEnabled: Boolean,
    val exactAlarmAllowed: Boolean,
    val enabled: Boolean,
)

/**
 * 一轮重排的结果。
 *
 * [note] 是给用户看的纯文本说明，降级（未获精确闹钟权限）、无课表、读取失败都走这里，
 * 绝不假装"已精确排定"（AC-33）。
 */
data class ReminderScheduleReport(
    val scheduledCount: Int,
    val enabled: Boolean,
    val exactAlarmAllowed: Boolean,
    val leadMinutes: Int,
    val note: String,
)

/** 通知里的时间文案：`08:10-09:40`；缺时间时用节次兜底，都没有则为"时间待定"。 */
internal fun timeTextOf(session: ClassSession): String = when {
    session.startTime != null && session.endTime != null -> "${session.startTime}-${session.endTime}"
    session.periodStart != null && session.periodEnd != null -> "第 ${session.periodStart}-${session.periodEnd} 节"
    else -> "时间待定"
}

/** 通知里的地点文案：复用 [SemesterFormatting]，保证与 App 内文案完全一致。 */
internal fun locationTextOf(isOnline: Boolean, room: String?, campus: String?): String =
    SemesterFormatting.location(room, campus, isOnline)
