package com.kebiao.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 某一周的课表求值结果。
 *
 * 刻意把"不进网格的课"单独放在 [unscheduled]：线上课程与无固定时间地点的备注课
 * **不得硬塞进网格**，必须在"线上 / 不排座"分区可查（AC-51 / PRD 第 9.6 节）。
 */
data class WeekSchedule(
    val semester: Semester?,
    /** 第几教学周；学期外为 null。 */
    val weekIndex: Int?,
    val monday: LocalDate?,
    /** 恒为 7 项（周一..周日），顺序即 DayOfWeek 1..7；学期外为空列表。 */
    val days: List<WeekDaySchedule>,
    val unscheduled: List<SessionOccurrence>,
)

/** 周视图的一列（一天）。 */
data class WeekDaySchedule(
    val date: LocalDate,
    val weekday: DayOfWeek,
    val items: List<SessionOccurrence>,
)
