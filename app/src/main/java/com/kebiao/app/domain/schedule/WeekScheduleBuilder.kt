package com.kebiao.app.domain.schedule

import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.model.WeekDaySchedule
import com.kebiao.app.domain.model.WeekSchedule
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * 周视图求值（AC-50 / AC-51）。
 *
 * 分区规则是这一层的核心职责：
 *  - **网格区**：`weekday != null` 且课程非线上 —— 才算"可排座"，进入 7 列网格；
 *  - **线上 / 不排座区**：`weekday == null` 或课程 `isOnline` —— 不得硬塞进网格。
 *
 * 注意排序里刻意不用 [LocalTime] 比较空值：无时间的课统一沉到当天列表末尾并按节次升序，
 * 保证渲染顺序稳定（UI 侧 `animateItem` 依赖稳定顺序，否则每次重组都会误判为"重排"）。
 */
object WeekScheduleBuilder {

    fun build(
        weekIndex: Int,
        semester: Semester,
        sessions: List<ClassSession>,
        coursesById: Map<Long, Course>,
        now: LocalTime,
    ): WeekSchedule {
        val monday = semester.mondayOfWeek(weekIndex)

        val inWeek = sessions.filter { weekIndex in it.weekNumbers }
        val gridded = inWeek.filter { session ->
            session.weekday != null && coursesById.courseOf(session).isOnline.not()
        }
        val unscheduled = inWeek.filter { session ->
            session.weekday == null || coursesById.courseOf(session).isOnline
        }

        val days = DayOfWeek.entries.map { weekday ->
            val date = monday.plusDays((weekday.value - 1).toLong())
            val items = gridded
                .filter { it.weekday == weekday }
                .map { ScheduleCalculator.occurrence(it, coursesById.courseOf(it), date, weekIndex, now) }
                .sortedWith(occurrenceOrder())
            WeekDaySchedule(date = date, weekday = weekday, items = items)
        }

        return WeekSchedule(
            semester = semester,
            weekIndex = weekIndex,
            monday = monday,
            days = days,
            unscheduled = unscheduled
                .map { ScheduleCalculator.occurrence(it, coursesById.courseOf(it), monday, weekIndex, now) }
                .sortedWith(occurrenceOrder()),
        )
    }

    /** 学期外（假期 / 未开始 / 已结束）的周视图：空网格，但保留分区字段语义。 */
    fun outsideSemester(semester: Semester?): WeekSchedule =
        WeekSchedule(semester = semester, weekIndex = null, monday = null, days = emptyList(), unscheduled = emptyList())
}

private fun occurrenceOrder(): Comparator<SessionOccurrence> =
    compareBy({ it.startTime == null }, { it.startTime }, { it.periodStart ?: Int.MAX_VALUE }, { it.courseName })

private fun Map<Long, Course>.courseOf(session: ClassSession): Course =
    this[session.courseId]
        ?: error("class_sessions.course_id=${session.courseId} 在 courses 中不存在（数据完整性已破坏）")
