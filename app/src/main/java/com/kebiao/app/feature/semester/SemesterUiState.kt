package com.kebiao.app.feature.semester

import com.kebiao.app.core.AppError
import java.time.LocalDate

/* =========================================================================
 * 多学期管理 UiState（Spec §7 + UIUX §7.4 §8）
 *
 * 不变式：至多一个 isCurrent = true（由 SemesterRepository 单点写入口保证，
 * UI 只做展示与切换动作，不参与不变式维护）。
 *
 * 「只有一个学期」是显式边界：当前学期仍显示标记，但不可点击切换。
 * ========================================================================= */

data class SemesterRow(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val isCurrent: Boolean
)

sealed interface SemesterUiState {

    data object Loading : SemesterUiState

    /** 首次使用，库里还没有任何学期。 */
    data object Empty : SemesterUiState

    data class Content(
        val rows: List<SemesterRow>,
        /** 正在切换中的目标学期（用于行内进度与禁止重复点击）。 */
        val switchingTo: Long? = null
    ) : SemesterUiState {
        /** 只有一个学期时不提供切换（避免点击后毫无变化）。 */
        val switchable: Boolean get() = rows.size > 1
    }

    data class Error(val error: AppError, val retryable: Boolean) : SemesterUiState
}
