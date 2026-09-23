package com.kebiao.app.feature.week

import com.kebiao.app.core.AppError
import com.kebiao.app.domain.model.SessionOccurrence
import java.time.LocalDate

/* =========================================================================
 * 周视图 UiState（Spec §7 + UIUX §7.3 §8）
 *
 * 约束：
 *  - 手机竖屏不硬塞 7 列：改逐日列 + 横向 Pager，时间轴 64dp 固定不随滑动。
 *  - 线上 / 不排座课程进独立分区，不进网格（AC-51）。
 *  - 单双周不命中的课默认不渲染（AC-50）。
 *
 * 行对齐：rows 是整页共享的节次行表，所有日列的 cells 与之等长且一一对应，
 * 这样各列的格子在同一水平线上，时间轴不需要跟着滑动。
 * ========================================================================= */

/** 网格内的一个格位。同一时段可能冲突两门课。 */
data class DayCell(
    val periodStart: Int,
    val occurrences: List<SessionOccurrence>
) {
    val isEmpty: Boolean get() = occurrences.isEmpty()
    val isConflict: Boolean get() = occurrences.size > 1
    val primary: SessionOccurrence? get() = occurrences.firstOrNull()
}

/** 一天的列数据。cells 与 WeekUiState.Content.rows 等长。 */
data class DayColumnData(
    /** 1 = 周一 … 7 = 周日，与 DayOfWeek.value 一致。 */
    val weekday: Int,
    val label: String,
    val isToday: Boolean,
    val cells: List<DayCell>
)

sealed interface WeekUiState {

    data object Loading : WeekUiState

    /** 整学期没有课表 -> inbox + 从教务系统导入引导。 */
    data class Empty(val semesterName: String?) : WeekUiState

    data class Content(
        val weekIndex: Int,
        val totalWeeks: Int,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val isCurrentWeek: Boolean,
        val rows: List<PeriodRow>,
        val days: List<DayColumnData>,
        /** 线上 / 无固定时间地点，不进网格（AC-51）。 */
        val onlineOrUnplaced: List<SessionOccurrence>
    ) : WeekUiState

    data class Error(val error: AppError, val retryable: Boolean) : WeekUiState
}
