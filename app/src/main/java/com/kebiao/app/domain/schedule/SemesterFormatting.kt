package com.kebiao.app.domain.schedule

import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 展示格式化（纯函数，无 Android 依赖，UI 与 Widget 共用同一套文案）。
 *
 * 文案纪律（Spec 第 7 节）：禁止"暂无数据"这类空洞占位；无地点、无时间必须给出
 * 可区分的明确文案："线上" / "地点待定" / "时间待定"。
 */
object SemesterFormatting {

    private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

    private val WEEKDAY_TEXT: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "周一",
        DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五",
        DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日",
    )

    private val TERM_TEXT: Map<Int, String> = mapOf(1 to "第一学期", 2 to "第二学期", 3 to "短学期")

    fun weekday(date: LocalDate): String = WEEKDAY_TEXT.getValue(date.dayOfWeek)

    fun weekday(dayOfWeek: DayOfWeek): String = WEEKDAY_TEXT.getValue(dayOfWeek)

    fun week(index: Int): String = "第 $index 周"

    /** 如 `第 3 周 周三`；学期外返回 `学期外`。 */
    fun weekAndWeekday(weekIndex: Int?, date: LocalDate): String =
        if (weekIndex == null) "学期外" else "${week(weekIndex)} ${weekday(date)}"

    fun dateLabel(date: LocalDate): String = date.format(MONTH_DAY)

    /** 如 `2026-2027 学年第一学期`；[Semester.name] 非空时优先使用它。 */
    fun semesterLabel(semester: Semester): String =
        semester.name.ifBlank {
            "${semester.academicYear} 学年${TERM_TEXT[semester.termIndex] ?: "第 ${semester.termIndex} 学期"}"
        }

    /**
     * 地点文案：线上 -> `线上`；有教室 -> 教室（带校区时 `临港校区 E教305`）；都没有 -> `地点待定`。
     */
    fun location(room: String?, campus: String?, isOnline: Boolean): String = when {
        isOnline -> "线上"
        !room.isNullOrBlank() && !campus.isNullOrBlank() -> "$campus $room"
        !room.isNullOrBlank() -> room
        else -> "地点待定"
    }

    fun location(occurrence: SessionOccurrence): String =
        location(occurrence.room, occurrence.campus, occurrence.isOnline)

    /** 时间文案：`08:10-09:40`；缺失 -> `时间待定`。 */
    fun timeRange(occurrence: SessionOccurrence): String = occurrence.timeRangeText ?: "时间待定"

    /** 节次文案：`1-2 节`；缺失 -> null（由调用方决定是否展示）。 */
    fun periods(session: ClassSession): String? = when {
        session.periodStart != null && session.periodEnd != null -> "${session.periodStart}-${session.periodEnd} 节"
        session.periodStart != null -> "第 ${session.periodStart} 节"
        else -> null
    }

    /** 教师文案：缺失 -> `教师待定`。 */
    fun teacher(name: String?): String = name?.takeIf { it.isNotBlank() } ?: "教师待定"

    /** 周次审计文案，用于导入核对视图：原始串 + 规范化集合一起给出。 */
    fun weeksAudit(session: ClassSession): String =
        "原始 `${session.weeksRaw}` -> 规范化 ${session.weekNumbers.display()}"
}
