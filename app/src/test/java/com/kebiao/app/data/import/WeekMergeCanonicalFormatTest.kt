package com.kebiao.app.data.import

import com.kebiao.app.data.import.parser.WeekExpressionParser
import com.kebiao.app.data.import.parser.WeekParseOutcome
import com.kebiao.app.domain.model.WeekParity
import com.kebiao.app.domain.model.WeekSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 抽取端「同槽合并」产出的**并集规范串**必须能被下游读懂 —— 这条是防线。
 *
 * ## 为什么需要这条测试
 * 真机第十四轮：教务把同一节课按周次段拆成多段渲染在同一个格子里
 * （`大学物理实验B(1)` 是 9-11 / 12 / 13 / 14 / 15-16 五段），抽取端把它们
 * 合并成一条，`weeksRaw` 换成**并集规范串**（`9-16周` / `2-16周` /
 * `3,5,7,9,11,13,15周`）。
 *
 * 合并端（JS，见 `feature/import/ImportWeekMerge.kt`）和解析端（本包的
 * [WeekExpressionParser]）是**两套实现**，中间只靠这一根字符串相连。
 * 若哪天合并端改了格式而下游读不懂，`ImportMapper` 会拿到错周次 ——
 * 这是最贵的那类失效：数据看着正常，实际整学期错课。所以这里钉死契约。
 *
 * ## 契约要点
 *  - 规范串只含「数字 / `,` / `-` / 结尾的 `周`」；
 *  - **绝不含「单」/「双」** —— 否则 `detectParity()` 会对已求过并集的集合
 *    再筛一遍，把 `{2..16}` 错杀成 `{2,4,..,16}`。
 */
class WeekMergeCanonicalFormatTest {

    private val totalWeeks = 16

    /** 合并端可能产出的串 -> 期望周集合。 */
    private val merged: List<Pair<String, Set<Int>>> = listOf(
        // (2周) ∪ (3-16周)
        "2,3-16周" to (2..16).toSet(),
        // 同一并集的另一种压缩写法（派单示例里两种都出现过）
        "2-16周" to (2..16).toSet(),
        // (9-11周) ∪ (12周) ∪ (13周) ∪ (14周) ∪ (15-16周)
        "9-16周" to (9..16).toSet(),
        // (2周) ∪ (3-8周)
        "2-8周" to (2..8).toSet(),
        // (3-15(单)周) 单条：单双已落进数字
        "3,5,7,9,11,13,15周" to setOf(3, 5, 7, 9, 11, 13, 15),
        // (2-16(双)周) 单条
        "2,4,6,8,10,12,14,16周" to setOf(2, 4, 6, 8, 10, 12, 14, 16),
        // (2周) 单条
        "2周" to setOf(2),
        // 9-11 与 15-16 两段不连续
        "9-11,15-16周" to setOf(9, 10, 11, 15, 16)
    )

    @Test
    fun `合并产出的规范串必须被解析出预期的周集合`() {
        merged.forEach { (raw, expect) ->
            val weekSet = WeekExpressionParser.parse(raw, totalWeeks)
            assertEquals(
                expect.sorted(),
                weekSet.sorted(),
                "「$raw」解析出的周集合不对（合并端与解析端的契约被破坏）"
            )
        }
    }

    @Test
    fun `规范串不含单双字样，parity 恒为 ALL（不再二次筛选）`() {
        merged.forEach { (raw, _) ->
            assertFalse("单" in raw, "规范串不该带「单」：$raw")
            assertFalse("双" in raw, "规范串不该带「双」：$raw")
            val outcome = WeekExpressionParser.tryParse(raw, totalWeeks)
            val success = outcome as? WeekParseOutcome.Success
                ?: error("「$raw」解析失败（合并端产出下游读不懂）：$outcome")
            assertEquals(
                WeekParity.ALL,
                success.parity,
                "「$raw」不该再被判出单双周 —— 并集已经把奇偶落进数字里了"
            )
        }
    }

    @Test
    fun `两种压缩写法对同一并集必须等价`() {
        val a = WeekExpressionParser.parse("2,3-16周", totalWeeks)
        val b = WeekExpressionParser.parse("2-16周", totalWeeks)
        assertEquals(b.sorted(), a.sorted())
        assertEquals((2..16).toList(), a.sorted())
    }

    @Test
    fun `原始括号形态（未合并路径）仍然按单双周解析`() {
        assertEquals(
            listOf(2, 4, 6, 8, 10, 12, 14, 16),
            WeekExpressionParser.parse("(2-16(双)周)", totalWeeks).sorted()
        )
        assertEquals(
            listOf(3, 5, 7, 9, 11, 13, 15),
            WeekExpressionParser.parse("(3-15(单)周)", totalWeeks).sorted()
        )
        assertEquals((2..16).toList(), WeekExpressionParser.parse("(2-16周)", totalWeeks).sorted())
    }

    @Test
    fun `合并后的一条覆盖原本多段的全部周次`() {
        // 真机 大学物理实验B(1)：五段 -> 合并成一条，周次不能丢
        val segments = listOf("(9-11周)", "(12周)", "(13周)", "(14周)", "(15-16周)")
        val union = segments.fold(emptySet<Int>()) { acc, seg ->
            acc + WeekExpressionParser.parse(seg, totalWeeks).weeks
        }
        val mergedSet = WeekExpressionParser.parse("9-16周", totalWeeks).weeks
        assertEquals(union.sorted(), mergedSet.sorted())
        assertEquals(WeekSet.of(9..16).sorted(), mergedSet.sorted())
    }
}
