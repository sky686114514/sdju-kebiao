package com.kebiao.app.data.import.parser

import com.kebiao.app.domain.model.WeekParity
import com.kebiao.app.domain.model.WeekSet

/**
 * 周次表达式解析器 —— 本项目**最容易出现沉默逻辑错误**的地方
 * （ARCHITECTURE.md 第 6.4 节 / `generated-code-failure-modes.md` 第 2 节）。
 *
 * 支持形态（PRD 第 4.2 节实测的真实数据）：
 * ```
 * "2-16 双周"       -> {2,4,6,8,10,12,14,16}
 * "3-15 单周"       -> {3,5,7,9,11,13,15}
 * "9-16"            -> {9..16}
 * "2,3-4,5,8,6-7"   -> {2..8}     乱序 + 重叠区间，需并集去重
 * "1-20"            -> {1..16}    上溢夹逼
 * "第1-16周"         -> {1..16}    带修饰
 * ```
 *
 * **纪律（Spec 第 11 节内嵌已知坑）**：解析失败必须显式报错，**严禁静默返回空集**。
 * 竞品"课表识别错导致连续四五天没课"的根因就是解析失败被上层 `catch` 吞成空集合。
 * 因此 [parse] 抛异常、[tryParse] 返回 [WeekParseOutcome.Failure]，两条路都不产出空集。
 *
 * 与 `tools/week_expr.py` 是逐行镜像实现，交叉验证见 `tools/verify_week_parser.py`。
 */
object WeekExpressionParser {

    /** 全角连字符 / 波浪号 / 中文"至""到"都当作区间分隔符（教务页面形态不统一）。 */
    private val HYPHEN_LIKE: Set<Char> = setOf('\uFF0D', '\u2014', '\u2013', '~', '\uFF5E', '至', '到')

    private val FULLWIDTH_DIGITS: CharRange = '\uFF10'..'\uFF19'

    /**
     * 规范入口（Spec 第 5.1 节契约）。
     *
     * @param totalWeeks 学期总周数；为 null 时以 `max(weeks)` 作为下界推导（边界用例 9）
     * @throws WeekExpressionException 解析失败
     */
    fun parse(raw: String, totalWeeks: Int? = null): WeekSet =
        when (val outcome = tryParse(raw, totalWeeks)) {
            is WeekParseOutcome.Success -> outcome.weekSet
            is WeekParseOutcome.Failure -> throw WeekExpressionException(outcome.error)
        }

    /** 无异常版本，供导入流水线按 `AppResult` 风格串联使用。 */
    fun tryParse(raw: String, totalWeeks: Int? = null): WeekParseOutcome {
        val cleaned = normalize(raw)
        if (cleaned.isBlank()) {
            return WeekParseOutcome.Failure(
                WeekExpressionError.EmptyWeeks(raw, "归一化后为空，无任何周次数字")
            )
        }

        val parity = detectParity(raw)

        val weeks: Set<Int> = when (val expanded = expandTokens(cleaned, raw)) {
            is ExpandResult.Err -> return WeekParseOutcome.Failure(expanded.error)
            is ExpandResult.Ok -> expanded.weeks
        }
        if (weeks.isEmpty()) {
            return WeekParseOutcome.Failure(
                WeekExpressionError.EmptyWeeks(raw, "按逗号切分后没有任何有效片段")
            )
        }

        val inferred = totalWeeks == null
        val effectiveTotal: Int = totalWeeks ?: weeks.max()

        val clamped = applyParity(weeks, parity).filter { it in 1..effectiveTotal }.toSet()
        if (clamped.isEmpty()) {
            return WeekParseOutcome.Failure(
                WeekExpressionError.OutOfSemesterRange(
                    raw = raw,
                    totalWeeks = effectiveTotal,
                    detail = "单双周过滤与夹逼后为空",
                )
            )
        }
        return WeekParseOutcome.Success(WeekSet(clamped), parity, inferred)
    }

    /**
     * 判定单双周。
     *
     * 顺序有意为之：先认"单双周"/"每周"（语义是"每周"），再认"单"，最后认"双"。
     * 若把"单"放在最前，教务系统偶尔输出的"单双周"会被误判成单周 —— 一处典型的沉默逻辑错误。
     */
    fun detectParity(raw: String): WeekParity = when {
        raw.contains("单双周") || raw.contains("每周") -> WeekParity.ALL
        raw.contains("单") -> WeekParity.ODD
        raw.contains("双") -> WeekParity.EVEN
        else -> WeekParity.ALL
    }

    /** 归一化：全角数字 -> 半角、全角逗号 -> 半角、各类连字符 -> `-`，其余字符全部丢弃。 */
    internal fun normalize(raw: String): String {
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '\uFF0C' || ch == ',' -> out.append(',')
                ch in HYPHEN_LIKE || ch == '-' -> out.append('-')
                ch in FULLWIDTH_DIGITS -> out.append('0' + (ch - '\uFF10'))
                ch in '0'..'9' -> out.append(ch)
                else -> Unit // "第""周""单双"等修饰字符在这里被丢弃
            }
        }
        return out.toString()
    }

    private fun applyParity(weeks: Set<Int>, parity: WeekParity): Set<Int> = when (parity) {
        WeekParity.ALL -> weeks
        WeekParity.ODD -> weeks.filterTo(HashSet()) { it % 2 == 1 }
        WeekParity.EVEN -> weeks.filterTo(HashSet()) { it % 2 == 0 }
    }

    private fun expandTokens(cleaned: String, raw: String): ExpandResult {
        val weeks = HashSet<Int>()
        for (token in cleaned.split(',')) {
            if (token.isEmpty()) continue
            val parts = token.split('-').filter { it.isNotEmpty() }
            when (parts.size) {
                1 -> weeks += parts[0].toIntOrNull()
                    ?: return ExpandResult.Err(
                        WeekExpressionError.MalformedToken(raw, token, "片段不是整数")
                    )

                2 -> {
                    val start = parts[0].toIntOrNull()
                    val end = parts[1].toIntOrNull()
                    if (start == null || end == null) {
                        return ExpandResult.Err(
                            WeekExpressionError.MalformedToken(raw, token, "区间端点不是整数")
                        )
                    }
                    if (start > end) {
                        return ExpandResult.Err(
                            WeekExpressionError.ReversedRange(raw, start, end, "区间起止倒置")
                        )
                    }
                    for (week in start..end) weeks += week
                }

                else -> return ExpandResult.Err(
                    WeekExpressionError.MalformedToken(raw, token, "含 ${parts.size} 个连字符分段")
                )
            }
        }
        return ExpandResult.Ok(weeks)
    }

    private sealed interface ExpandResult {
        data class Ok(val weeks: Set<Int>) : ExpandResult
        data class Err(val error: WeekExpressionError) : ExpandResult
    }
}
