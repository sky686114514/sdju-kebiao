package com.kebiao.app.domain.schedule

import com.kebiao.app.Fixtures
import com.kebiao.app.domain.model.Semester
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SemesterResolver] 单测 —— "当前学期"的推断规则。
 *
 * 两条必须锁死的语义（ARCHITECTURE 第 6.6 节）：
 *  1. 用户手动指定的 `is_current` 是权威，不得被日期推断覆盖（否则假期里切到旧学期会被改回去）；
 *  2. 无显式标记时，假期里**上一个已结束学期优先于下一个未开始学期**。
 */
class SemesterResolverTest {

    private val springPrev = semester(id = 2L, start = "2026-02-23", termIndex = 2)
    private val autumn = Fixtures.SEMESTER
    private val springNext = semester(id = 3L, start = "2027-02-22", termIndex = 2)

    @Test
    fun `fixture date assumptions hold`() {
        // 下面几个用例依赖这些区间端点，先锁死前提。
        assertEquals(LocalDate.parse("2026-06-14"), springPrev.endDate)
        assertEquals(LocalDate.parse("2027-01-03"), autumn.endDate)
        assertEquals(LocalDate.parse("2027-02-22"), springNext.startDate)
    }

    @Test
    fun `an explicit current semester wins even when today sits inside another semester`() {
        // 2026-04-01 落在 springPrev 区间内，但 autumn 手动标记为当前 -> 必须采用 autumn。
        val resolved = SemesterResolver.resolve(listOf(springPrev, autumn), LocalDate.parse("2026-04-01"))
        assertEquals(autumn.id, resolved?.id)
    }

    @Test
    fun `an explicit current semester stays authoritative even far outside all ranges`() {
        val only = semester(id = 31L, start = "2026-09-14", termIndex = 1, isCurrent = true)
        val resolved = SemesterResolver.resolve(listOf(only), LocalDate.parse("2030-01-01"))
        assertEquals(only.id, resolved?.id, "用户手动切换不得被日期推断覆盖")
    }

    @Test
    fun `during a vacation the previous semester is preferred over the upcoming one`() {
        // 2026-08-15 介于 springPrev 结束与 springNext 开始之间，两边都不命中。
        val resolved = SemesterResolver.resolve(listOf(springPrev, springNext), LocalDate.parse("2026-08-15"))
        assertEquals(springPrev.id, resolved?.id, "假期应优先回看刚结束的学期")
    }

    @Test
    fun `an in-term semester is picked over any vacation fallback`() {
        val resolved = SemesterResolver.infer(listOf(springPrev, autumn), LocalDate.parse("2026-09-20"))
        assertEquals(autumn.id, resolved?.id)
    }

    @Test
    fun `when several semesters cover today the highest term index wins`() {
        val term1 = semester(id = 41L, start = "2026-09-14", termIndex = 1)
        val term3 = semester(id = 43L, start = "2026-09-14", termIndex = 3)
        val picked = SemesterResolver.infer(listOf(term1, term3), LocalDate.parse("2026-09-20"))
        assertEquals(term3.id, picked?.id)
    }

    @Test
    fun `resolve falls back to inference when the stored current flag is corrupt`() {
        // 两条 is_current = true 属数据损坏：resolve 不猜，交给 infer 收敛。
        val corruptA = semester(id = 51L, start = "2026-09-14", termIndex = 1, isCurrent = true)
        val corruptB = semester(id = 53L, start = "2026-09-14", termIndex = 3, isCurrent = true)
        val resolved = SemesterResolver.resolve(listOf(corruptA, corruptB), LocalDate.parse("2026-09-20"))
        assertEquals(corruptB.id, resolved?.id)
    }

    @Test
    fun `the single future semester is returned when nothing is in the past`() {
        val resolved = SemesterResolver.infer(listOf(springNext), LocalDate.parse("2026-09-20"))
        assertEquals(springNext.id, resolved?.id)
    }

    @Test
    fun `an empty store resolves to null instead of inventing a semester`() {
        assertNull(SemesterResolver.infer(emptyList(), LocalDate.parse("2026-09-20")))
        assertNull(SemesterResolver.resolve(emptyList(), LocalDate.parse("2026-09-20")))
    }

    private fun semester(
        id: Long,
        start: String,
        termIndex: Int,
        isCurrent: Boolean = false,
    ): Semester = Semester(
        id = id,
        name = "学期 $id",
        academicYear = "2026-2027",
        termIndex = termIndex,
        startDate = LocalDate.parse(start),
        totalWeeks = 16,
        isCurrent = isCurrent,
    )
}
