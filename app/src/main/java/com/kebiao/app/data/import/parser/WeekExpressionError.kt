package com.kebiao.app.data.import.parser

import com.kebiao.app.domain.model.WeekParity
import com.kebiao.app.domain.model.WeekSet

/**
 * 周次解析的失败原因。
 *
 * 刻意把"为什么失败"分类而不是只给一个字符串：导入核对视图要按类别给出可操作提示
 * （例如"反向区间"通常意味着教务页面列顺序变了，"全部越界"通常意味着学期总周数取错）。
 */
sealed class WeekExpressionError(
    val raw: String,
    val detail: String,
) {
    /** 归一化后没有任何数字（空串 / 只有"周" / 只有分隔符）。 */
    class EmptyWeeks(raw: String, detail: String) : WeekExpressionError(raw, detail)

    /** 片段形状非法（如 `1-2-3`、单独的 `-`）。 */
    class MalformedToken(raw: String, val token: String, detail: String) :
        WeekExpressionError(raw, detail)

    /** 区间起止倒置（如 `16-2`）。 */
    class ReversedRange(raw: String, val start: Int, val end: Int, detail: String) :
        WeekExpressionError(raw, detail)

    /** 单双周过滤 + 学期范围夹逼后为空（如 totalWeeks=16 时的 `17-20`）。 */
    class OutOfSemesterRange(raw: String, val totalWeeks: Int, detail: String) :
        WeekExpressionError(raw, detail)

    /** 面向人的说明，用于核对视图与日志（纯文本，无 emoji）。 */
    val message: String
        get() = when (this) {
            is EmptyWeeks -> "周次串 '$raw' 中没有任何可识别的周次"
            is MalformedToken -> "周次串 '$raw' 含无法识别的片段 '$token'"
            is ReversedRange -> "周次串 '$raw' 的区间 $start-$end 起止倒置"
            is OutOfSemesterRange -> "周次串 '$raw' 在 1..$totalWeeks 范围内没有命中任何一周"
        }
}

/** [WeekExpressionParser.parse] 在解析失败时抛出。**绝不返回空集**。 */
class WeekExpressionException(val error: WeekExpressionError) :
    IllegalArgumentException(error.message)

/**
 * 无异常版本的结果（ARCHITECTURE.md 第 6.4 节的 `Result<WeekSet>` 语义）。
 *
 * @param totalWeeksInferred 教务系统未给学期总周数、由 `max(weeks)` 推导时为 true
 *        （此时必须记 `import_verify_diff`，不能当作"完全正常"）
 */
sealed interface WeekParseOutcome {

    data class Success(
        val weekSet: WeekSet,
        val parity: WeekParity,
        val totalWeeksInferred: Boolean,
    ) : WeekParseOutcome

    data class Failure(val error: WeekExpressionError) : WeekParseOutcome
}
