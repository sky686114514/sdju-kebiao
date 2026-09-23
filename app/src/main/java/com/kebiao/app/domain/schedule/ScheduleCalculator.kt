package com.kebiao.app.domain.schedule

import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.ClassStatus
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.model.TodaySchedule
import java.time.LocalDate
import java.time.LocalTime

/**
 * "今天有哪些课"求值算法（ARCHITECTURE.md 第 6.5 节）。
 *
 * 纯函数：`date` 与 `now` 全部由参数注入，函数体内不出现 `LocalDate.now()` /
 * `System.currentTimeMillis()`，因此可以在 JVM 单测里任意构造"某天某刻"。
 *
 * 复杂度 O(n)：单学期 [ClassSession] 仅数十行，采用内存过滤而非 SQL 周次查询，
 * 与 Spec 第 6 节的索引策略一致（`week_numbers` 刻意不建索引）。
 */
object ScheduleCalculator {

    /**
     * 给定日期 -> 当天全部课次（含状态判定），按"有时间优先、时间升序、节次升序、课程名"排序。
     *
     * 星期为 null 的课（如"军事技能"）不会出现在任何一天：它没有可归属的日期，
     * 属于"线上 / 不排座"分区，由 [WeekScheduleBuilder] 承载（AC-51）。
     */
    fun occurrencesOn(
        date: LocalDate,
        semester: Semester,
        sessions: List<ClassSession>,
        coursesById: Map<Long, Course>,
        now: LocalTime,
    ): List<SessionOccurrence> {
        val week = semester.weekOf(date) ?: return emptyList()
        val weekday = date.dayOfWeek

        return sessions
            .asSequence()
            .filter { it.weekday == weekday && week in it.weekNumbers }
            .map { session ->
                occurrence(session, coursesById.resolve(session.courseId), date, week, now)
            }
            .sortedWith(
                compareBy<SessionOccurrence>(
                    { it.startTime == null },
                    { it.startTime },
                    { it.periodStart ?: Int.MAX_VALUE },
                    { it.courseName },
                )
            )
            .toList()
    }

    /**
     * 组装单个课次（供当天列表与周视图共用，保证状态判定只有一份实现）。
     */
    fun occurrence(
        session: ClassSession,
        course: Course,
        date: LocalDate,
        weekOfSemester: Int,
        now: LocalTime,
    ): SessionOccurrence {
        val status = statusOf(session.startTime, session.endTime, now)
        return SessionOccurrence(
            sessionId = session.id,
            courseId = course.id,
            courseName = course.name,
            courseCode = course.code,
            accentIndex = course.accentIndex,
            date = date,
            weekOfSemester = weekOfSemester,
            weekday = date.dayOfWeek,
            periodStart = session.periodStart,
            periodEnd = session.periodEnd,
            startTime = session.startTime,
            endTime = session.endTime,
            campus = session.campus,
            room = session.room,
            teacher = session.teacher,
            isOnline = course.isOnline,
            remark = session.remark,
            status = status,
        )
    }

    /**
     * 时间状态判定。
     *
     * 边界语义：开始时刻即视为"进行中"（不含未开始），结束时刻即视为"已结束"
     * —— 与"距开始 15 分钟提醒"配合时不会出现"提醒发出但卡片仍显示未开始"的错位。
     */
    fun statusOf(startTime: LocalTime?, endTime: LocalTime?, now: LocalTime): ClassStatus {
        if (startTime == null || endTime == null) return ClassStatus.UNKNOWN_TIME
        val nowMinutes = now.toMinutesOfDay()
        return when {
            nowMinutes < startTime.toMinutesOfDay() -> ClassStatus.UPCOMING
            nowMinutes < endTime.toMinutesOfDay() -> ClassStatus.ONGOING
            else -> ClassStatus.FINISHED
        }
    }

    /**
     * 组装 [TodaySchedule]，把三种"没有课"显式区分开（Spec 第 7 节 / PRD 第 10 节）：
     * 尚未导入 / 学期之外 / 学期内今天确实没课。
     *
     * @param hasAnySession 该学期是否存在任何 [ClassSession]（用于区分"未导入"与"今天没课"）
     */
    fun todaySchedule(
        date: LocalDate,
        semester: Semester?,
        sessions: List<ClassSession>,
        coursesById: Map<Long, Course>,
        now: LocalTime,
        hasAnySession: Boolean,
    ): TodaySchedule {
        if (semester == null || !hasAnySession) return TodaySchedule.notImported(date)
        val week = semester.weekOf(date) ?: return TodaySchedule.outsideSemester(date, semester)
        val items = occurrencesOn(date, semester, sessions, coursesById, now)
        return if (items.isEmpty()) TodaySchedule.noClassToday(date, semester, week)
        else TodaySchedule.content(date, semester, week, items)
    }
}

private fun LocalTime.toMinutesOfDay(): Int = hour * 60 + minute

/**
 * 取课程；缺失即数据不一致，显式抛错而不是静默跳过。
 * 静默跳过会让 UI 少显示一门课且毫无信号 —— 正是"沉默逻辑错误"。
 */
private fun Map<Long, Course>.resolve(courseId: Long): Course =
    this[courseId] ?: error("class_sessions.course_id=$courseId 在 courses 中不存在（数据完整性已破坏）")
