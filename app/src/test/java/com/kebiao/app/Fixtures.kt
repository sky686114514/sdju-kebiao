package com.kebiao.app

import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionSource
import com.kebiao.app.domain.model.WeekSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 测试夹具：PRD 第 4.2 节 / Spec 第 2.1 节公布的**真实课表数据**。
 *
 * ## 诚实声明（重要）
 *
 * 文档只公布了各课程的**周次、教室、教师**，未公布「自动化专业导论」「大学物理实验B(1)」
 * 「航空与航天」「军事技能」的星期与节次。测试必须有具体输入才能断言，
 * 因此这些未公布字段在夹具里按**可测性需要显式赋值** —— 它们是"测试输入"，
 * **不代表教务系统的真实取值**，不得被当作产品事实引用。
 *
 * 与 Python 侧的对应关系：`tools/fetch_schedule.py --sample` 产出的夹具与本文档
 * 共享同一批已公布数据，二者互为交叉验证（周次 / 教室 / 教师三项必须一致）。
 */
object Fixtures {

    /** 学期起始日：第 1 周的周一（2026-09-14 确为周一，见 [SemesterWeekOfTest] 的断言）。 */
    val START_DATE: LocalDate = LocalDate.parse("2026-09-14")

    val SEMESTER: Semester = Semester(
        id = 1L,
        name = "2026-2027 学年第一学期",
        academicYear = "2026-2027",
        termIndex = 1,
        startDate = START_DATE,
        totalWeeks = 16,
        isCurrent = true,
    )

    fun mondayOfWeek(week: Int): LocalDate = SEMESTER.mondayOfWeek(week)

    fun wednesdayOfWeek(week: Int): LocalDate = mondayOfWeek(week).plusDays(2)

    fun thursdayOfWeek(week: Int): LocalDate = mondayOfWeek(week).plusDays(3)

    fun tuesdayOfWeek(week: Int): LocalDate = mondayOfWeek(week).plusDays(1)

    // ---------- L2 课程 ----------

    val PHYSICS: Course = Course(
        id = 10L,
        semesterId = SEMESTER.id,
        name = "大学物理B(1)",
        code = "053017P1-15",
        totalHours = 48,
        isOnline = false,
    )

    val INTRO: Course = Course(
        id = 20L,
        semesterId = SEMESTER.id,
        name = "自动化专业导论与职业生涯规划",
        code = null,
        totalHours = null,
        isOnline = false,
    )

    val PHYSICS_LAB: Course = Course(
        id = 30L,
        semesterId = SEMESTER.id,
        name = "大学物理实验B(1)",
        code = null,
        totalHours = null,
        isOnline = false,
    )

    val ONLINE: Course = Course(
        id = 40L,
        semesterId = SEMESTER.id,
        name = "航空与航天",
        code = null,
        totalHours = null,
        isOnline = true,
    )

    val MILITARY: Course = Course(
        id = 50L,
        semesterId = SEMESTER.id,
        name = "军事技能",
        code = null,
        totalHours = null,
        isOnline = false,
    )

    val COURSES: List<Course> = listOf(PHYSICS, INTRO, PHYSICS_LAB, ONLINE, MILITARY)

    val COURSES_BY_ID: Map<Long, Course> = COURSES.associateBy { it.id }

    // ---------- L3 上课安排 ----------

    /** 形状 1：第 2 周 E教305，第 3-16 周 B105（AC-02）。 */
    val PHYSICS_SESSIONS: List<ClassSession> = listOf(
        session(
            id = 101L, courseId = PHYSICS.id, weekday = DayOfWeek.WEDNESDAY,
            periodStart = 1, periodEnd = 2, start = "08:10", end = "09:40",
            room = "E教305", teacher = "李彬彬", weeksRaw = "2", weeks = WeekSet.of(2..2),
        ),
        session(
            id = 102L, courseId = PHYSICS.id, weekday = DayOfWeek.WEDNESDAY,
            periodStart = 1, periodEnd = 2, start = "08:10", end = "09:40",
            room = "B105", teacher = "李彬彬", weeksRaw = "3-16", weeks = WeekSet.of(3..16),
        ),
    )

    /** 形状 1：同一门课四个互斥周次段，教师随周次轮换（AC-03）。 */
    val INTRO_SESSIONS: List<ClassSession> = listOf(
        session(
            id = 201L, courseId = INTRO.id, weekday = DayOfWeek.TUESDAY,
            periodStart = 3, periodEnd = 4, start = "10:10", end = "11:40",
            room = "D教203", teacher = "陈国初", weeksRaw = "2", weeks = WeekSet.of(2..2),
        ),
        session(
            id = 202L, courseId = INTRO.id, weekday = DayOfWeek.TUESDAY,
            periodStart = 3, periodEnd = 4, start = "10:10", end = "11:40",
            room = "B203", teacher = "蒋璐峥", weeksRaw = "3-4", weeks = WeekSet.of(3..4),
        ),
        session(
            id = 203L, courseId = INTRO.id, weekday = DayOfWeek.TUESDAY,
            periodStart = 3, periodEnd = 4, start = "10:10", end = "11:40",
            room = "B203", teacher = "陈国初", weeksRaw = "5,8", weeks = WeekSet.of(5, 8),
        ),
        session(
            id = 204L, courseId = INTRO.id, weekday = DayOfWeek.TUESDAY,
            periodStart = 3, periodEnd = 4, start = "10:10", end = "11:40",
            room = "B203", teacher = "于妍", weeksRaw = "6-7", weeks = WeekSet.of(6..7),
        ),
    )

    /** 形状 2：每周实验室不同。 */
    val PHYSICS_LAB_SESSIONS: List<ClassSession> = listOf(
        labSession(301L, "9-11", 9..11, "204(实验室2)"),
        labSession(302L, "12", 12..12, "202(实验室1)"),
        labSession(303L, "13", 13..13, "309(实验室11)"),
        labSession(304L, "14", 14..14, "305(实验室7)"),
        labSession(305L, "15-16", 15..16, "308(实验室10)"),
    )

    /** 形状 4：纯线上课程，无实体教室。 */
    val ONLINE_SESSIONS: List<ClassSession> = listOf(
        session(
            id = 401L, courseId = ONLINE.id, weekday = null,
            periodStart = null, periodEnd = null, start = null, end = null,
            room = null, teacher = null, weeksRaw = "5-16", weeks = WeekSet.of(5..16),
        ),
    )

    /** 形状 5：无固定时间地点（weekday / 节次 / 时间 / 教室 全部可空）。 */
    val MILITARY_SESSIONS: List<ClassSession> = listOf(
        session(
            id = 501L, courseId = MILITARY.id, weekday = null,
            periodStart = null, periodEnd = null, start = null, end = null,
            room = null, teacher = null, weeksRaw = "1-16", weeks = WeekSet.of(1..16),
        ),
    )

    /** 全部 L3，与 Python 夹具的 13 条 ClassSession 一一对应。 */
    val ALL_SESSIONS: List<ClassSession> =
        PHYSICS_SESSIONS + INTRO_SESSIONS + PHYSICS_LAB_SESSIONS + ONLINE_SESSIONS + MILITARY_SESSIONS

    /** 单双周隔离用的最小夹具：同一天、同一时段，一条单周一条双周。 */
    fun oddEvenPair(): Pair<ClassSession, ClassSession> = Pair(
        session(
            id = 601L, courseId = PHYSICS.id, weekday = DayOfWeek.MONDAY,
            periodStart = 1, periodEnd = 2, start = "08:10", end = "09:40",
            room = "A101", teacher = "单周老师", weeksRaw = "1-16 单周", weeks = WeekSet((1..16 step 2).toSet()),
        ),
        session(
            id = 602L, courseId = PHYSICS.id, weekday = DayOfWeek.MONDAY,
            periodStart = 1, periodEnd = 2, start = "08:10", end = "09:40",
            room = "A102", teacher = "双周老师", weeksRaw = "2-16 双周", weeks = WeekSet((2..16 step 2).toSet()),
        ),
    )

    private fun labSession(id: Long, weeksRaw: String, weeks: IntRange, room: String): ClassSession =
        session(
            id = id, courseId = PHYSICS_LAB.id, weekday = DayOfWeek.THURSDAY,
            periodStart = 5, periodEnd = 6, start = "13:30", end = "15:00",
            room = room, teacher = null, weeksRaw = weeksRaw, weeks = WeekSet.of(weeks),
        )

    @Suppress("LongParameterList")
    private fun session(
        id: Long,
        courseId: Long,
        weekday: DayOfWeek?,
        periodStart: Int?,
        periodEnd: Int?,
        start: String?,
        end: String?,
        room: String?,
        teacher: String?,
        weeksRaw: String,
        weeks: WeekSet,
    ): ClassSession = ClassSession(
        id = id,
        courseId = courseId,
        weekday = weekday,
        periodStart = periodStart,
        periodEnd = periodEnd,
        startTime = start?.let { LocalTime.parse(it) },
        endTime = end?.let { LocalTime.parse(it) },
        campus = if (room == null) null else "临港校区",
        room = room,
        teacher = teacher,
        weeksRaw = weeksRaw,
        weekNumbers = weeks,
        source = SessionSource.IMPORT,
        importBatchId = 1L,
    )
}
