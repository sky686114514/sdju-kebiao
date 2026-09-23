package com.kebiao.app.domain.model

/**
 * 规范化后的教学周次集合（1-based）。
 *
 * 这是本项目"单双周 / 换教室"能被正确求值的最小信息单元：一条 [ClassSession] 携带一个
 * [WeekSet]，同一门课的互斥周次行就是多个 [WeekSet] 不重叠的 [ClassSession]
 * （Spec 第 2.1 节建模铁律）。
 *
 * 纯 Kotlin，无任何 Android 依赖，可在 JVM 单测里穷举。
 */
@JvmInline
value class WeekSet(val weeks: Set<Int>) {

    val size: Int get() = weeks.size

    val isEmpty: Boolean get() = weeks.isEmpty()

    val minOrNull: Int? get() = weeks.minOrNull()

    val maxOrNull: Int? get() = weeks.maxOrNull()

    operator fun contains(week: Int): Boolean = week in weeks

    /** 升序列表，便于展示与快照断言。 */
    fun sorted(): List<Int> = weeks.sorted()

    /**
     * 落库形态 `",2,4,6,8,"` —— 前后各加一个分隔符，使 LIKE 匹配不会出现
     * "2" 命中 "12" 的经典误判（ARCHITECTURE.md 第 6.3 节）。
     */
    fun normalized(): String = weeks.sorted().joinToString(separator = ",", prefix = ",", postfix = ",")

    /** 审计友好的紧凑展示，例如 `2,4,6` 或 `3-7`。 */
    fun display(): String {
        val ordered = weeks.sorted()
        if (ordered.isEmpty()) return "无"
        val contiguous = ordered == (ordered.first()..ordered.last()).toList()
        return if (contiguous && ordered.size > 1) "${ordered.first()}-${ordered.last()}"
        else ordered.joinToString(",")
    }

    override fun toString(): String = normalized()

    companion object {
        val EMPTY: WeekSet = WeekSet(emptySet())

        fun of(range: IntRange): WeekSet = WeekSet(range.toSet())

        fun of(vararg weeks: Int): WeekSet = WeekSet(weeks.toSet())

        /**
         * 从落库形态 `",2,4,6,"` 还原。
         *
         * 非法输入不静默成空集 —— 抛 [IllegalArgumentException]，由调用方翻译为
         * 数据损坏错误（`generated-code-failure-modes.md` 第 2 节：沉默逻辑错误最昂贵）。
         */
        fun parseNormalized(text: String): WeekSet {
            val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val weeks = parts.map { token ->
                token.toIntOrNull() ?: throw IllegalArgumentException("week_numbers 含非数字片段: '$token' in '$text'")
            }
            return WeekSet(weeks.toSet())
        }
    }
}
