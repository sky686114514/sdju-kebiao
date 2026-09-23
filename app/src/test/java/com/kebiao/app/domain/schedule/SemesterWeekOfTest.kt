package com.kebiao.app.domain.schedule

import com.kebiao.app.Fixtures
import com.kebiao.app.domain.model.Semester
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [Semester.weekOf] 的边界单测。
 *
 * `weekOf` 是本项目**唯一**接触"日期 -> 教学周"映射的地方，也是 ARCHITECTURE 第 14.2 节
 * 点名要求"变异定向加固"的 off-by-one 高发点：`days / 7` 若漏掉 `+ 1`，开学当天会被算成
 * 第 0 周，随后整学期每门课都错一周 —— 一个不会崩、不会报错、只会静默给错答案的缺陷。
 *
 * 全部为纯函数断言，不依赖系统时钟。
 */
class SemesterWeekOfTest {

    private val semester = Fixtures.SEMESTER

    @Test
    fun `the fixture start date really is a Monday`() {
        // 夹具前提：若起始日不是周一，下面所有边界断言的语义都不成立。
        assertEquals(DayOfWeek.MONDAY, Fixtures.START_DATE.dayOfWeek)
        assertEquals(Fixtures.START_DATE, semester.mondayOfWeek(1))
    }

    @Test
    fun `the opening day is week 1 not week 0`() {
        // 关键 off-by-one 断言：漏 +1 会得到 0，此处必须为 1。
        assertEquals(1, semester.weekOf(Fixtures.START_DATE))
        assertEquals(1, semester.weekOf(Fixtures.START_DATE.plusDays(6)))
        assertEquals(2, semester.weekOf(Fixtures.START_DATE.plusDays(7)))
    }

    @Test
    fun `dates before the semester start map to null`() {
        assertNull(semester.weekOf(Fixtures.START_DATE.minusDays(1)))
        assertNull(semester.weekOf(Fixtures.START_DATE.minusYears(1)))
    }

    @Test
    fun `week 16 covers its Monday through Sunday and nothing beyond`() {
        val lastMonday = semester.mondayOfWeek(16)
        assertEquals(16, semester.weekOf(lastMonday))
        assertEquals(16, semester.weekOf(lastMonday.plusDays(6)))
        assertNull(semester.weekOf(lastMonday.plusDays(7)))
    }

    @Test
    fun `every teaching week maps both its Monday and its Sunday back to itself`() {
        // 穷举 1..16：既抓首周 off-by-one，也抓跨周错位（把相邻周吞进同一周）。
        for (week in 1..semester.totalWeeks) {
            val monday = semester.mondayOfWeek(week)
            val sunday = monday.plusDays(6)
            assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek, "第 $week 周周一落点不是周一")
            assertEquals(week, semester.weekOf(monday), "第 $week 周周一应回映射到 $week")
            assertEquals(week, semester.weekOf(sunday), "第 $week 周周日应回映射到 $week")
        }
    }

    @Test
    fun `end date is the Sunday of the last teaching week`() {
        assertEquals(LocalDate.parse("2027-01-03"), semester.endDate)
        assertEquals(DayOfWeek.SUNDAY, semester.endDate.dayOfWeek)
        assertEquals(16, semester.weekOf(semester.endDate))
        assertNull(semester.weekOf(semester.endDate.plusDays(1)))
    }

    @Test
    fun `mondayOfWeek is monotonic and exactly seven days apart`() {
        for (week in 2..semester.totalWeeks) {
            assertEquals(
                semester.mondayOfWeek(week - 1).plusDays(7),
                semester.mondayOfWeek(week),
                "第 ${week - 1} 周与第 $week 周的周一应差 7 天",
            )
        }
    }

    @Test
    fun `construction rejects impossible semester parameters`() {
        assertFailsWith<IllegalArgumentException> {
            Semester(
                id = 99L, name = "空学期", academicYear = "x", termIndex = 1,
                startDate = Fixtures.START_DATE, totalWeeks = 0, isCurrent = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Semester(
                id = 98L, name = "越界学期", academicYear = "x", termIndex = 4,
                startDate = Fixtures.START_DATE, totalWeeks = 16, isCurrent = false,
            )
        }
    }
}
