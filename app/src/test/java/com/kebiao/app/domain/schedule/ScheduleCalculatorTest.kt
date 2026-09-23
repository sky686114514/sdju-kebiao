package com.kebiao.app.domain.schedule

import com.kebiao.app.Fixtures
import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.ClassStatus
import com.kebiao.app.domain.model.SessionSource
import com.kebiao.app.domain.model.TodayEmptyReason
import com.kebiao.app.domain.model.WeekSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ScheduleCalculator] 单测 —— 覆盖 PRD 第 4.2 节的真实课表形态与 Spec 第 7 节的空状态语义。
 *
 * 这些断言专门针对"沉默逻辑错误"：教室换行取错、教师轮换错位、单双周同时出现、
 * 空状态四种文案糊成一种、以及缺课程数据时静默少显示一门课。
 */
class ScheduleCalculatorTest {

    private val now: LocalTime = LocalTime.NOON

    // ---------- 形状 1：同课不同周换教室（AC-02） ----------

    @Test
    fun `AC-02 physics moves from E_305 in week 2 to B105 from week 3 on`() {
        val day2 = occurrences(Fixtures.wednesdayOfWeek(2), Fixtures.PHYSICS_SESSIONS)
        val day3 = occurrences(Fixtures.wednesdayOfWeek(3), Fixtures.PHYSICS_SESSIONS)

        assertEquals(1, day2.size, "第 2 周周三应恰好一条物理课")
        assertEquals(1, day3.size, "第 3 周周三应恰好一条物理课")
        assertEquals("E教305", day2.single().room)
        assertEquals("B105", day3.single().room)
        assertNotEquals(day2.single().room, day3.single().room, "第 2 周与第 3 周教室不同")
        assertEquals("李彬彬", day2.single().teacher)
        assertEquals(2, day2.single().weekOfSemester)
        assertEquals(3, day3.single().weekOfSemester)
        assertEquals(LocalTime.of(8, 10), day2.single().startTime)
        assertEquals(1, day2.single().periodStart)
    }

    // ---------- 形状 1：同课不同周换教师（AC-03） ----------

    @Test
    fun `AC-03 intro course teacher rotates across four mutually exclusive week segments`() {
        val expectedTeacher = mapOf(
            2 to "陈国初", 3 to "蒋璐峥", 4 to "蒋璐峥",
            5 to "陈国初", 6 to "于妍", 7 to "于妍", 8 to "陈国初",
        )
        expectedTeacher.forEach { (week, teacher) ->
            val items = occurrences(Fixtures.tuesdayOfWeek(week), Fixtures.INTRO_SESSIONS)
            assertEquals(1, items.size, "第 $week 周导论课应恰好一条，实得 ${items.size}")
            assertEquals(teacher, items.single().teacher, "第 $week 周导论课教师不符")
            assertEquals(week, items.single().weekOfSemester)
        }

        assertEquals("D教203", occurrences(Fixtures.tuesdayOfWeek(2), Fixtures.INTRO_SESSIONS).single().room)
        assertEquals("B203", occurrences(Fixtures.tuesdayOfWeek(3), Fixtures.INTRO_SESSIONS).single().room)

        // 第 9 周不落在任何一段内：若区间被写宽把整学期覆盖，此断言会失败。
        assertTrue(
            occurrences(Fixtures.tuesdayOfWeek(9), Fixtures.INTRO_SESSIONS).isEmpty(),
            "第 9 周导论课不应存在",
        )
    }

    // ---------- 单双周隔离 ----------

    @Test
    fun `a single-week and a double-week session never both land on the same Monday`() {
        val (odd, even) = Fixtures.oddEvenPair()
        val pair = listOf(odd, even)

        val day1 = occurrences(Fixtures.mondayOfWeek(1), pair)
        val day2 = occurrences(Fixtures.mondayOfWeek(2), pair)

        assertEquals(1, day1.size, "第 1 周周一应恰好一条")
        assertEquals(1, day2.size, "第 2 周周一应恰好一条")
        assertEquals(odd.id, day1.single().sessionId)
        assertEquals(even.id, day2.single().sessionId)
        assertEquals("A101", day1.single().room)
        assertEquals("A102", day2.single().room)
    }

    // ---------- 时间状态机 ----------

    @Test
    fun `status treats the start instant as ongoing and the end instant as finished`() {
        val start = LocalTime.of(8, 10)
        val end = LocalTime.of(9, 40)
        assertEquals(ClassStatus.UPCOMING, ScheduleCalculator.statusOf(start, end, LocalTime.of(8, 9)))
        assertEquals(ClassStatus.ONGOING, ScheduleCalculator.statusOf(start, end, start))
        assertEquals(ClassStatus.ONGOING, ScheduleCalculator.statusOf(start, end, LocalTime.of(9, 39)))
        assertEquals(ClassStatus.FINISHED, ScheduleCalculator.statusOf(start, end, end))
        assertEquals(ClassStatus.FINISHED, ScheduleCalculator.statusOf(start, end, LocalTime.of(23, 59)))
    }

    @Test
    fun `sessions without times are unknown rather than guessed`() {
        assertEquals(ClassStatus.UNKNOWN_TIME, ScheduleCalculator.statusOf(null, LocalTime.of(9, 40), now))
        assertEquals(ClassStatus.UNKNOWN_TIME, ScheduleCalculator.statusOf(LocalTime.of(8, 10), null, now))
        assertEquals(ClassStatus.UNKNOWN_TIME, ScheduleCalculator.statusOf(null, null, now))
    }

    // ---------- 四种可区分结果 ----------

    @Test
    fun `today schedule splits into content and three distinguishable empty reasons`() {
        val content = ScheduleCalculator.todaySchedule(
            Fixtures.wednesdayOfWeek(2), Fixtures.SEMESTER, Fixtures.PHYSICS_SESSIONS,
            Fixtures.COURSES_BY_ID, now, hasAnySession = true,
        )
        assertEquals(1, content.items.size)
        assertEquals(2, content.weekOfSemester)
        assertNull(content.emptyReason)

        val sundayOfWeek2 = Fixtures.mondayOfWeek(2).plusDays(6)
        val noClass = ScheduleCalculator.todaySchedule(
            sundayOfWeek2, Fixtures.SEMESTER, Fixtures.PHYSICS_SESSIONS,
            Fixtures.COURSES_BY_ID, now, hasAnySession = true,
        )
        assertEquals(TodayEmptyReason.NO_CLASS_TODAY, noClass.emptyReason)
        assertEquals(2, noClass.weekOfSemester)

        val holiday = ScheduleCalculator.todaySchedule(
            LocalDate.parse("2027-03-01"), Fixtures.SEMESTER, Fixtures.PHYSICS_SESSIONS,
            Fixtures.COURSES_BY_ID, now, hasAnySession = true,
        )
        assertEquals(TodayEmptyReason.OUTSIDE_SEMESTER, holiday.emptyReason)
        assertNull(holiday.weekOfSemester)

        val noSemester = ScheduleCalculator.todaySchedule(
            Fixtures.wednesdayOfWeek(2), null, emptyList(), emptyMap(), now, hasAnySession = false,
        )
        assertEquals(TodayEmptyReason.NOT_IMPORTED, noSemester.emptyReason)

        val noSessions = ScheduleCalculator.todaySchedule(
            Fixtures.wednesdayOfWeek(2), Fixtures.SEMESTER, emptyList(), Fixtures.COURSES_BY_ID,
            now, hasAnySession = false,
        )
        assertEquals(TodayEmptyReason.NOT_IMPORTED, noSessions.emptyReason)
    }

    // ---------- 排序稳定性 ----------

    @Test
    fun `occurrences sort timed before untimed then by start time then by period`() {
        val list = listOf(
            stubSession(id = 901L, start = LocalTime.of(13, 30), end = LocalTime.of(15, 0), period = 5),
            stubSession(id = 902L, start = null, end = null, period = 1),
            stubSession(id = 903L, start = LocalTime.of(8, 10), end = LocalTime.of(9, 40), period = 3),
        )
        val ordered = occurrences(Fixtures.mondayOfWeek(1), list)
        assertEquals(listOf(903L, 901L, 902L), ordered.map { it.sessionId })
    }

    // ---------- 边界与数据完整性 ----------

    @Test
    fun `sessions without a weekday never appear on any date`() {
        val floating = Fixtures.MILITARY_SESSIONS + Fixtures.ONLINE_SESSIONS
        for (week in 1..Fixtures.SEMESTER.totalWeeks) {
            for (offset in 0..6) {
                val date = Fixtures.mondayOfWeek(week).plusDays(offset.toLong())
                val items = occurrences(date, floating)
                assertTrue(items.isEmpty(), "$date 不应出现无固定星期的课次")
            }
        }
    }

    @Test
    fun `dates outside the semester yield no occurrences`() {
        assertTrue(occurrences(LocalDate.parse("2027-03-01"), Fixtures.ALL_SESSIONS).isEmpty())
        assertTrue(
            occurrences(Fixtures.START_DATE.minusDays(1), Fixtures.ALL_SESSIONS).isEmpty(),
            "开学前一天不应有课",
        )
    }

    @Test
    fun `a dangling course reference fails loudly instead of silently dropping the session`() {
        assertFailsWith<IllegalStateException> {
            ScheduleCalculator.occurrencesOn(
                Fixtures.wednesdayOfWeek(2), Fixtures.SEMESTER, Fixtures.PHYSICS_SESSIONS,
                emptyMap(), now,
            )
        }
    }

    // ---------- 辅助 ----------

    private fun occurrences(date: LocalDate, sessions: List<ClassSession>) =
        ScheduleCalculator.occurrencesOn(date, Fixtures.SEMESTER, sessions, Fixtures.COURSES_BY_ID, now)

    private fun stubSession(id: Long, start: LocalTime?, end: LocalTime?, period: Int): ClassSession =
        ClassSession(
            id = id,
            courseId = Fixtures.PHYSICS.id,
            weekday = DayOfWeek.MONDAY,
            periodStart = period,
            periodEnd = period,
            startTime = start,
            endTime = end,
            campus = null,
            room = null,
            teacher = null,
            weeksRaw = "1",
            weekNumbers = WeekSet.of(1),
            source = SessionSource.MANUAL,
            importBatchId = null,
        )
}
