package com.kebiao.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * L4 课次 —— **计算产物，不落库**，每次求值现算（Spec 第 6 节四层分层）。
 *
 * 它由 [ClassSession] 在某一天命中后组装而来，携带渲染所需的一切信息，
 * 因此 UI 与 Widget 不需要回查数据库、也不需要自己判断"今天"。
 */
data class SessionOccurrence(
    /** 来源 [ClassSession] 的 id，用于 LazyColumn 的稳定 key（禁止用下标）。 */
    val sessionId: Long,
    val courseId: Long,
    val courseName: String,
    val courseCode: String?,
    val accentIndex: Int,
    val date: LocalDate,
    val weekOfSemester: Int,
    val weekday: DayOfWeek,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val campus: String?,
    val room: String?,
    val teacher: String?,
    val isOnline: Boolean,
    val remark: String?,
    val status: ClassStatus,
) {
    /**
     * 列表稳定 key：会话 id + 日期。
     * 同一门课同一天可能有多条（不同周次行的边界重叠不会发生，但同一天连排两节会），
     * 因此用 sessionId 而非 courseId。
     */
    val occurrenceKey: String get() = "$sessionId@$date"

    /** 时间显示：`08:10-09:40`；无时间返回 null（由 UI 决定"时间待定"文案）。 */
    val timeRangeText: String?
        get() = if (startTime != null && endTime != null) "$startTime-$endTime" else null
}
