package com.kebiao.app.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * L1 学期。
 *
 * 时间数学的唯一入口是 [weekOf]：本项目真正依赖"当前时间"的日期计算只有这一处，
 * 把它抽成纯函数（输入只有 `startDate` / `totalWeeks` / `date`）之后，就能在 JVM 单测里
 * 穷举第 1 周 / 边界周 / 学期外，而完全不依赖系统时钟（ARCHITECTURE.md 第 6.5 节）。
 *
 * 铁律：domain 层禁止出现 `LocalDate.now()` / `System.currentTimeMillis()`。
 */
data class Semester(
    val id: Long,
    val name: String,
    val academicYear: String,
    /** 1 = 秋（第一学期）/ 2 = 春（第二学期）/ 3 = 短学期。 */
    val termIndex: Int,
    /** 第 1 周的周一，例如 2026-09-14。 */
    val startDate: LocalDate,
    val totalWeeks: Int,
    val isCurrent: Boolean,
) {
    init {
        require(totalWeeks > 0) { "totalWeeks 必须为正数，实得 $totalWeeks" }
        require(termIndex in 1..3) { "termIndex 只允许 1..3，实得 $termIndex" }
    }

    /** 学期最后一个教学周的周日（含）。 */
    val endDate: LocalDate
        get() = startDate.plusWeeks((totalWeeks - 1).toLong()).plusDays(6)

    /** 第 [week] 周的周一。 */
    fun mondayOfWeek(week: Int): LocalDate = startDate.plusWeeks((week - 1).toLong())

    /**
     * 给定日期 -> 第几教学周；越界（学期开始前 / 教学周结束后）返回 null。
     *
     * 注意 `+ 1`：第 1 周对应 [startDate, startDate+6]，`days / 7` 若不加 1 会把开学当天算成第 0 周
     * —— 这是本项目点名要求"变异定向加固"的 off-by-one 高发点（ARCHITECTURE.md 第 14.2 节）。
     */
    fun weekOf(date: LocalDate): Int? {
        if (date.isBefore(startDate)) return null
        val days = ChronoUnit.DAYS.between(startDate, date)
        val week = (days / 7).toInt() + 1
        return if (week in 1..totalWeeks) week else null
    }
}
