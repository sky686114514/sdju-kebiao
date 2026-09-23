package com.kebiao.app.data.import

import com.kebiao.app.core.AppResult
import com.kebiao.app.domain.model.ImportSourceKind
import com.kebiao.app.domain.model.WeekSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `OVERLAPPING_WEEKS` 告警判据的回归单测（真机第 14 轮）。
 *
 * ## 被钉死的行为
 *
 * 教务系统把同一门课按**不同时间段**分别排课（"周一 1-2 节"与"周二 3-4 节"各有各的周次段），
 * 它们的周次重叠是**完全正常**的。旧实现把同一门课的全部安排两两求周次交集且不看时段，
 * 于是一次导入刷出 50 条误报，把"同一时段真的撞车"这唯一有风险的信号淹没了。
 *
 * 因此本文件的**核心是反向断言**：不同时段 + 周次重叠 ⇒ **不得**出告警。
 * 若哪天有人把判据改回"只看周次"，第 1、2 条用例必须立刻失败。
 */
class ImportMapperOverlapTest {

    private val mapper = ImportMapper()

    private val startDate: LocalDate = LocalDate.parse("2026-09-14")

    // ---------- 端到端：map() 产出的 ImportIssue ----------

    @Test
    fun `different weekday with identical weeks produces no overlapping alert`() {
        // 同一门课，周一与周二各排一次，周次完全重合 —— 教务的正常排课，不是冲突。
        val plan = plan(
            session(weekday = 1, periodStart = 1, periodEnd = 2, weeksRaw = "1-16"),
            session(weekday = 2, periodStart = 1, periodEnd = 2, weeksRaw = "1-16"),
        )
        assertTrue(overlaps(plan).isEmpty(), "不同星期不得判定为周次重叠，实得 ${overlaps(plan)}")
    }

    @Test
    fun `same weekday but different period produces no overlapping alert`() {
        // 同一天的第 1-2 节与第 3-4 节：时段不同，周次重叠正常。
        val plan = plan(
            session(weekday = 1, periodStart = 1, periodEnd = 2, weeksRaw = "1-16"),
            session(weekday = 1, periodStart = 3, periodEnd = 4, weeksRaw = "1-16"),
        )
        assertTrue(overlaps(plan).isEmpty(), "同星期不同节次不得判定为周次重叠，实得 ${overlaps(plan)}")
    }

    @Test
    fun `same weekday and same period with overlapping weeks produces exactly one alert with slot`() {
        val plan = plan(
            session(weekday = 1, periodStart = 1, periodEnd = 2, weeksRaw = "1-8"),
            session(weekday = 1, periodStart = 1, periodEnd = 2, weeksRaw = "5-16"),
        )
        val issues = overlaps(plan)
        assertEquals(1, issues.size, "同时段周次重叠应恰好 1 条告警，实得 ${issues.size}")
        val detail = issues[0].detail
        assertTrue(detail.contains("同一门课在同一时段"), "告警文案必须写明是同一时段：$detail")
        assertTrue(detail.contains("周一"), "告警文案必须含星期以便核对：$detail")
        assertTrue(detail.contains("第1-2节"), "告警文案必须含节次以便核对：$detail")
        assertTrue(detail.contains("周同时命中"), "告警文案必须保留重合周次：$detail")
        assertEquals("1-8 / 5-16", issues[0].raw, "raw 保持两条原始周次串的格式")
    }

    @Test
    fun `same weekday and same period with disjoint weeks produces no alert`() {
        // 实验室按周次段切分（1-8 与 9-16）在同一时段：周次互斥，不算冲突。
        val plan = plan(
            session(weekday = 1, periodStart = 1, periodEnd = 2, weeksRaw = "1-8"),
            session(weekday = 1, periodStart = 1, periodEnd = 2, weeksRaw = "9-16"),
        )
        assertTrue(overlaps(plan).isEmpty(), "同时段但周次互斥不得告警，实得 ${overlaps(plan)}")
    }

    @Test
    fun `reported false positive shape is now silent`() {
        // 真机第 14 轮截图里的原始形态：双周段与全周段，分属不同时间段。
        val plan = plan(
            session(weekday = 3, periodStart = 1, periodEnd = 2, weeksRaw = "(2-16(双)周)"),
            session(weekday = 4, periodStart = 3, periodEnd = 4, weeksRaw = "(3-16周)"),
        )
        assertTrue(
            overlaps(plan).isEmpty(),
            "真机误报形态必须不再告警（周次确实相交，但时段不同），实得 ${overlaps(plan)}",
        )
    }

    // ---------- 判据谓词本身：sameSlot ----------

    @Test
    fun `sameSlot is false across weekdays even with identical period`() {
        assertFalse(
            mapper.sameSlot(
                planned(weekday = DayOfWeek.MONDAY, periodStart = 1, periodEnd = 2),
                planned(weekday = DayOfWeek.TUESDAY, periodStart = 1, periodEnd = 2),
            ),
            "星期不同即不同时段",
        )
    }

    @Test
    fun `sameSlot is false when periodStart matches but periodEnd differs`() {
        assertFalse(
            mapper.sameSlot(
                planned(weekday = DayOfWeek.MONDAY, periodStart = 1, periodEnd = 2),
                planned(weekday = DayOfWeek.MONDAY, periodStart = 1, periodEnd = 4),
            ),
            "节次区间不同即不同时段（不得只看 periodStart）",
        )
    }

    @Test
    fun `sameSlot is true for identical weekday and period`() {
        assertTrue(
            mapper.sameSlot(
                planned(weekday = DayOfWeek.MONDAY, periodStart = 1, periodEnd = 2),
                planned(weekday = DayOfWeek.MONDAY, periodStart = 1, periodEnd = 2),
            ),
        )
    }

    @Test
    fun `sameSlot degenerates to startTime when period is missing`() {
        val a = planned(weekday = DayOfWeek.MONDAY, periodStart = null, periodEnd = null, start = "08:10")
        val b = planned(weekday = DayOfWeek.MONDAY, periodStart = null, periodEnd = null, start = "08:10")
        val c = planned(weekday = DayOfWeek.MONDAY, periodStart = null, periodEnd = null, start = "10:10")
        assertTrue(mapper.sameSlot(a, b), "节次缺失时退化为上课时间相同")
        assertFalse(mapper.sameSlot(a, c), "节次缺失但上课时间不同即不同时段")
    }

    @Test
    fun `sameSlot is false when time info is entirely absent`() {
        // 时间待定的两条：无法判定冲突，宁可漏报也不误报（误报会淹掉真信号）。
        val a = planned(weekday = null, periodStart = null, periodEnd = null, start = null)
        val b = planned(weekday = null, periodStart = null, periodEnd = null, start = null)
        assertFalse(mapper.sameSlot(a, b), "时段信息全无时不得判定为同一时段")
    }

    // ---------- 辅助 ----------

    private fun overlaps(plan: ImportPlan): List<ImportIssue> =
        plan.issues.filter { it.kind == ImportIssue.Kind.OVERLAPPING_WEEKS }

    private fun plan(vararg sessions: RawSession): ImportPlan {
        val raw = RawSchedule(
            kind = ImportSourceKind.JSON_FILE,
            semester = SemesterDraft("2026-2027 学年第一学期", "2026-2027", 1, startDate, 16),
            courses = listOf(
                RawCourse(name = "大学物理B(1)", code = null, totalHours = null, isOnline = false, sessions = sessions.toList()),
            ),
            payloadRef = null,
        )
        return when (val result = mapper.map(raw)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> throw AssertionError("映射应成功，实得失败：${result.error.userMessage}")
        }
    }

    private fun session(
        weekday: Int?,
        periodStart: Int?,
        periodEnd: Int?,
        weeksRaw: String,
    ): RawSession = RawSession(
        weekday = weekday,
        periodStart = periodStart,
        periodEnd = periodEnd,
        // 节次缺失的用例靠 startTime 退化判定，故这里统一给一个时间，避免混入 MISSING_TIME。
        startTime = "08:10",
        endTime = "09:40",
        campus = null,
        room = "A101",
        teacher = null,
        weeksRaw = weeksRaw,
    )

    private fun planned(
        weekday: DayOfWeek?,
        periodStart: Int?,
        periodEnd: Int?,
        start: String? = "08:10",
    ): PlannedSession = PlannedSession(
        weekday = weekday,
        periodStart = periodStart,
        periodEnd = periodEnd,
        startTime = start?.let { LocalTime.parse(it) },
        endTime = null,
        campus = null,
        room = "A101",
        teacher = null,
        weeksRaw = "1-16",
        weekNumbers = WeekSet((1..16).toSet()),
        remark = null,
    )
}
