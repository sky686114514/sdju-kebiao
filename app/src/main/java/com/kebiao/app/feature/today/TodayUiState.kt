package com.kebiao.app.feature.today

import com.kebiao.app.core.AppError
import com.kebiao.app.domain.model.SessionOccurrence
import java.time.LocalDate
import java.time.LocalTime

/* =========================================================================
 * 今日课程 UiState（Spec §7 + ARCHITECTURE §12）
 *
 * 四种「没有课」必须是四个不同分支而不是一个 Empty —— 文案必须可区分：
 *   NotImported      尚未导入课表   -> 「还没有导入课表」+ 去导入
 *   OutsideSemester  学期之外       -> 「学期还没开始」/「正在假期中」（两者文案不同）
 *   NoClassToday     今天确实没课   -> 「今天没有课程」+ 下节课真实提示 + 查看本周课表
 *   Content(allFinished = true)     -> 今日已上完（在列表上方加完成提示，不替换列表）
 * ========================================================================= */

sealed interface TodayUiState {

    data object Loading : TodayUiState

    /** 尚未导入课表。提供「去导入」引导。 */
    data object NotImported : TodayUiState

    /**
     * 今天落在学期之外。
     * @param notStartedYet true = 学期尚未开始；false = 学期已结束（假期中）
     */
    data class OutsideSemester(
        val notStartedYet: Boolean,
        val semesterName: String?,
        val startDate: LocalDate?
    ) : TodayUiState

    /** 今天确实没课。nextClass 用于「下节课：周四 08:10 …」。 */
    data class NoClassToday(
        val semesterId: Long,
        val semesterName: String,
        val date: LocalDate,
        val week: Int,
        val totalWeeks: Int,
        val nextClass: SessionOccurrence?
    ) : TodayUiState

    /**
     * 正常内容。
     * @param week 教学周；理论上必有值（有课次则必在学期内），但**不编造**——
     *             异常时为 null，标题退化为「今天」而不是显示错误的周次。
     * @param allFinished 今日课程全部已下课 -> 「今日已上完」提示条（不替换列表）
     */
    data class Content(
        val semesterId: Long,
        val semesterName: String,
        val date: LocalDate,
        val week: Int?,
        val totalWeeks: Int,
        /** 当前时刻，由 TimeProvider 注入（UI 层禁止调 LocalTime.now()）。用于课程卡状态机判定。 */
        val now: LocalTime,
        val items: List<SessionOccurrence>,
        val allFinished: Boolean
    ) : TodayUiState

    data class Error(val error: AppError, val retryable: Boolean) : TodayUiState
}
