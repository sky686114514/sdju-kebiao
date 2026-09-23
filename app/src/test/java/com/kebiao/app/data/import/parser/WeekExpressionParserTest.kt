package com.kebiao.app.data.import.parser

import com.kebiao.app.domain.model.WeekParity
import com.kebiao.app.domain.model.WeekSet
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [WeekExpressionParser] 的穷举边界单测。
 *
 * 用例与 `tools/verify_week_parser.py` 的第 1 层（规范期望）与第 2 层（失败路径）
 * **逐条一一对应**，两侧对同一批输入必须给出同样的集合或同样的错误分类 —— 这是
 * Kotlin 主实现与 Python 参考实现之间唯一可自动化的交叉验证通道。
 *
 * 核心价值在于失败路径：**任何非法串都必须显式抛错，绝不能静默返回空集**。
 * 竞品"课表识别错 -> 连续四五天没课"的根因就是这里被吞成 `emptySet()`。
 */
class WeekExpressionParserTest {

    private val totalWeeks = 16

    /** (原始串, 期望周次升序列表)，来源 PRD 4.2 实测数据 + ARCHITECTURE 6.4 边界用例。 */
    private val expected: List<Pair<String, List<Int>>> = listOf(
        "2-16 双周" to weeks(2, 4, 6, 8, 10, 12, 14, 16),
        "3-15 单周" to weeks(3, 5, 7, 9, 11, 13, 15),
        "9-16" to range(9, 16),
        "1-16" to range(1, 16),
        "第1-16周" to range(1, 16),
        "2,3-4,5,8,6-7" to range(2, 8),
        "1-20" to range(1, 16),
        "5-16" to range(5, 16),
        "3-16" to range(3, 16),
        "9-11" to weeks(9, 10, 11),
        "12" to weeks(12),
        "13" to weeks(13),
        "14" to weeks(14),
        "15-16" to weeks(15, 16),
        "2" to weeks(2),
        "3-4" to weeks(3, 4),
        "5,8" to weeks(5, 8),
        "6-7" to weeks(6, 7),
        "1-16 单周" to weeks(1, 3, 5, 7, 9, 11, 13, 15),
        "\uFF11-\uFF18" to range(1, 8),
        "\uFF12\uFF0C\uFF13-\uFF14" to weeks(2, 3, 4),
        "2\uFF5E6" to range(2, 6),
        "1-16 每周" to range(1, 16),
        "1-16 单双周" to range(1, 16),

        // 教务新版 SPA 形态（前端同步：周次可能被括号包裹）。
        // 两个支撑点缺一不可：normalize 丢弃括号等非必需字符，
        // 而 detectParity 读**原始串**，故 "(双周)" 里的"双"仍会被识别。
        "(2-16)(双周)" to weeks(2, 4, 6, 8, 10, 12, 14, 16),
        "(2-8周)" to range(2, 8),
        "3-15周(单)" to weeks(3, 5, 7, 9, 11, 13, 15),
        "2,4,6周" to weeks(2, 4, 6),
        "1-16周" to range(1, 16),
    )

    /** (非法串, 期望的错误分类)。断言"抛错"且分类正确，绝不允许静默返回空集。 */
    private val mustFail: List<Pair<String, KClass<out WeekExpressionError>>> = listOf(
        "" to WeekExpressionError.EmptyWeeks::class,
        "   " to WeekExpressionError.EmptyWeeks::class,
        "\u5468" to WeekExpressionError.EmptyWeeks::class,
        "," to WeekExpressionError.EmptyWeeks::class,
        "16-2" to WeekExpressionError.ReversedRange::class,
        "5-1,9-10" to WeekExpressionError.ReversedRange::class,
        "1-2-3" to WeekExpressionError.MalformedToken::class,
        "-" to WeekExpressionError.MalformedToken::class,
        "17-20" to WeekExpressionError.OutOfSemesterRange::class,
        "0" to WeekExpressionError.OutOfSemesterRange::class,
        "17-19 \u5355\u5468" to WeekExpressionError.OutOfSemesterRange::class,

        // 纵深防御：即便前端的两类前置守卫（含字母不认 / 纯数字且不含周单双不认）失效，
        // 这两类噪声也必须**显式报错**，绝不能被吞成空集或歪成一个荒谬的周次。
        "(2026-2027-1)-533008G1-19" to WeekExpressionError.MalformedToken::class,
        "105" to WeekExpressionError.OutOfSemesterRange::class,
    )

    // ---------- 第 1 层：规范期望 ----------

    @Test
    fun `every published week expression parses to the documented set`() {
        assertTrue(expected.size >= 24, "规范期望用例应覆盖 24 条以上，实得 ${expected.size}")
        expected.forEach { (raw, want) ->
            val got = WeekExpressionParser.parse(raw, totalWeeks).weeks.sorted()
            assertEquals(want, got, "周次串 '$raw' 解析结果不符")
        }
    }

    @Test
    fun `overflow range is clamped to totalWeeks instead of being dropped`() {
        // 若夹逼方向写反（filter { it >= totalWeeks }），这里会得到 {16}，属典型沉默逻辑错误。
        assertEquals(range(1, 16), WeekExpressionParser.parse("1-20", totalWeeks).weeks.sorted())
    }

    @Test
    fun `messy ordering and overlapping ranges are unioned and de-duplicated`() {
        assertEquals(range(2, 8), WeekExpressionParser.parse("2,3-4,5,8,6-7", totalWeeks).weeks.sorted())
    }

    // ---------- 第 2 层：失败路径（本文件的核心） ----------

    @Test
    fun `every malformed expression throws the classified error instead of returning an empty set`() {
        assertTrue(mustFail.size >= 11, "失败用例应覆盖 11 条以上，实得 ${mustFail.size}")
        mustFail.forEach { (raw, type) ->
            val ex = assertFailsWith<WeekExpressionException>("'$raw' 必须抛 WeekExpressionException") {
                WeekExpressionParser.parse(raw, totalWeeks)
            }
            assertTrue(
                type.isInstance(ex.error),
                "'$raw' 期望错误 ${type.simpleName}，实得 ${ex.error::class.simpleName}",
            )
        }
    }

    @Test
    fun `tryParse never yields a success with an empty week set`() {
        mustFail.forEach { (raw, _) ->
            val outcome = WeekExpressionParser.tryParse(raw, totalWeeks)
            val failure = assertIsFailure(outcome, "'$raw' tryParse 必须返回 Failure")
            assertEquals(raw, failure.error.raw, "'$raw' 错误对象必须原样保留原始串以便审计")
            assertTrue(failure.error.detail.isNotBlank(), "'$raw' 失败原因说明不得为空")
        }
    }

    // ---------- 单双周 ----------

    @Test
    fun `parity detection reads odd-even labels in the safe order`() {
        // 顺序敏感：把"单"放在"单双周"之前会把"单双周"误判为单周 —— 一处沉默逻辑错误。
        assertEquals(WeekParity.ALL, WeekExpressionParser.detectParity("1-16 单双周"), "单双周 = 每周")
        assertEquals(WeekParity.ALL, WeekExpressionParser.detectParity("1-16 每周"))
        assertEquals(WeekParity.ALL, WeekExpressionParser.detectParity("1-16"))
        assertEquals(WeekParity.ODD, WeekExpressionParser.detectParity("3-15 单周"))
        assertEquals(WeekParity.EVEN, WeekExpressionParser.detectParity("2-16 双周"))
    }

    /**
     * 教务新版 SPA 会把周次渲染成 `(2-16)(双周)` 这类带括号形态（前端同步）。
     *
     * 前端已在 `tools/week_expr.py` 上实测过这五条，这里在 **Kotlin 主实现**上
     * 独立复算一遍：镜像脚本不是交付产物，**两侧必须各自验过**，不能只信一侧。
     *
     * 断言集合的同时另断言 [WeekParity]，是因为"集合对"可能掩盖"单双周丢了"——
     * 若 detectParity 退化成恒返回 ALL，`(2-16)(双周)` 会变成 1..16 全集，
     * 这种偏差必须能被单独捕获，而不是混在一眼看不出差错的集合断言里。
     */
    @Test
    fun `parenthesized SPA week expressions keep the parity label`() {
        val cases = listOf(
            Triple("(2-16)(双周)", WeekParity.EVEN, weeks(2, 4, 6, 8, 10, 12, 14, 16)),
            Triple("(2-8周)", WeekParity.ALL, range(2, 8)),
            Triple("3-15周(单)", WeekParity.ODD, weeks(3, 5, 7, 9, 11, 13, 15)),
            Triple("2,4,6周", WeekParity.ALL, weeks(2, 4, 6)),
            Triple("1-16周", WeekParity.ALL, range(1, 16)),
        )
        cases.forEach { (raw, parity, want) ->
            val outcome = WeekExpressionParser.tryParse(raw, totalWeeks)
            val ok = assertIsSuccess(outcome, "'$raw' 必须解析成功（SPA 带括号形态）")
            assertEquals(want, ok.weekSet.sorted(), "'$raw' 周次集合不符")
            assertEquals(parity, ok.parity, "'$raw' 单双周判定不符（括号里的单/双不得丢失）")
        }
    }

    @Test
    fun `odd and even week sets are mutually exclusive and correctly filtered`() {
        val odd = WeekExpressionParser.parse("3-15 单周", totalWeeks).weeks
        val even = WeekExpressionParser.parse("2-16 双周", totalWeeks).weeks
        assertTrue(odd.all { it % 2 == 1 }, "单周集合含偶数")
        assertTrue(even.all { it % 2 == 0 }, "双周集合含奇数")
        assertTrue((odd intersect even).isEmpty(), "单双周集合相交")
        assertFalse(4 in odd, "单周不应含第 4 周")
        assertFalse(5 in even, "双周不应含第 5 周")
        assertEquals(weeks(3, 5, 7, 9, 11, 13, 15), odd.sorted())
        assertEquals(weeks(2, 4, 6, 8, 10, 12, 14, 16), even.sorted())
    }

    // ---------- 落库形态 ----------

    @Test
    fun `normalized storage form round trips and guards the like substring trap`() {
        val set = WeekExpressionParser.parse("2,4,6,8", totalWeeks)
        assertEquals(weeks(2, 4, 6, 8), set.weeks.sorted())
        assertEquals(",2,4,6,8,", set.normalized())
        assertEquals(set.weeks, WeekSet.parseNormalized(set.normalized()).weeks)
        assertEquals("2,4,6,8", set.display())
        assertEquals("3-16", WeekExpressionParser.parse("3-16", totalWeeks).display())

        assertEquals(",2,12,", WeekSet.of(2, 12).normalized())
        assertTrue(likeMatches(",2,12,", 2))
        assertTrue(likeMatches(",2,12,", 12))
        assertFalse(likeMatches(",12,", 2), "第 2 周不得命中仅含第 12 周的落库串")
        assertFalse(likeMatches(",2,", 12), "第 12 周不得命中仅含第 2 周的落库串")
    }

    @Test
    fun `parseNormalized rejects corrupted storage text`() {
        assertFailsWith<IllegalArgumentException> { WeekSet.parseNormalized(",2,x,") }
    }

    // ---------- 缺 totalWeeks 时的下界推导 ----------

    @Test
    fun `when totalWeeks is absent the parser infers it from the max week and flags it`() {
        val outcome = WeekExpressionParser.tryParse("3-16", null)
        val ok = assertIsSuccess(outcome, "缺 totalWeeks 时应成功")
        assertEquals(range(3, 16), ok.weekSet.sorted())
        assertTrue(ok.totalWeeksInferred, "以 max(weeks) 推导时必须标记 inferred=true")
        assertEquals(WeekParity.ALL, ok.parity)
    }

    @Test
    fun `supplying totalWeeks marks the result as not inferred`() {
        val ok = assertIsSuccess(WeekExpressionParser.tryParse("3-16", totalWeeks), "应成功")
        assertFalse(ok.totalWeeksInferred, "显式给定 totalWeeks 时不得标记 inferred")
        assertEquals(range(3, 16), ok.weekSet.sorted())
    }

    // ---------- 辅助 ----------

    private fun likeMatches(normalizedStorage: String, week: Int): Boolean =
        normalizedStorage.contains(",$week,")

    private fun weeks(vararg values: Int): List<Int> = values.sorted()

    private fun range(from: Int, to: Int): List<Int> = (from..to).toList()

    private fun assertIsSuccess(outcome: WeekParseOutcome, message: String): WeekParseOutcome.Success =
        outcome as? WeekParseOutcome.Success
            ?: throw AssertionError("$message，实得 $outcome")

    private fun assertIsFailure(outcome: WeekParseOutcome, message: String): WeekParseOutcome.Failure =
        outcome as? WeekParseOutcome.Failure
            ?: throw AssertionError("$message，实得 $outcome")
}
