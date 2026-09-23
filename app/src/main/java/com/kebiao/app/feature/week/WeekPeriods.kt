package com.kebiao.app.feature.week

import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.ui.components.formatTime

/* =========================================================================
 * 周视图的节次行表
 *
 * 时间轴 64dp 固定不随 Pager 滑动，所以行表必须是**整页共享**的稳定结构，
 * 否则各日列的格子会错位。
 *
 * 行来源 = 设计给定的规范行 ∪ 本周数据里出现的节次（防御教务系统新增节次）。
 * 规范行取自真实课表的六个时间刻度：
 *   08:10 / 10:00 / 12:30 / 14:20 / 15:55 / 17:30
 * ========================================================================= */

data class PeriodRow(
    val periodStart: Int,
    val periodEnd: Int,
    val startLabel: String,
    val endLabel: String
) {
    val periodLabel: String get() = if (periodStart == periodEnd) "$periodStart" else "$periodStart-$periodEnd"
}

object WeekPeriods {

    private val canonical: List<PeriodRow> = listOf(
        PeriodRow(1, 2, "08:10", "09:40"),
        PeriodRow(3, 4, "10:00", "11:30"),
        PeriodRow(5, 6, "12:30", "14:00"),
        PeriodRow(7, 8, "14:20", "15:50"),
        PeriodRow(9, 10, "15:55", "17:25"),
        PeriodRow(10, 11, "17:30", "19:05")
    )

    /**
     * 规范行 ∪ 数据实测行，按起始节次升序。
     * 数据里出现规范表之外的节次时，用该课次自带的起止时间补一行。
     */
    fun resolve(discovered: List<SessionOccurrence>): List<PeriodRow> {
        val merged = LinkedHashMap<Int, PeriodRow>()
        canonical.forEach { merged[it.periodStart] = it }

        discovered.forEach { occurrence ->
            val start = occurrence.periodStart ?: return@forEach
            if (merged.containsKey(start)) return@forEach
            merged[start] = PeriodRow(
                periodStart = start,
                periodEnd = occurrence.periodEnd ?: start,
                startLabel = formatTime(occurrence.startTime).ifEmpty { "--:--" },
                endLabel = formatTime(occurrence.endTime).ifEmpty { "--:--" }
            )
        }
        return merged.values.sortedBy { it.periodStart }
    }
}
