package com.kebiao.app.feature.semester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.data.repository.SemesterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* =========================================================================
 * 多学期管理 ViewModel
 *
 * 切换只有一条写路径：SemesterRepository.switchTo(id)（Room 事务，移动 is_current）。
 * 切换成功后 Room 表变更会自动触发 TodayViewModel / WeekViewModel 的 Flow 重发，
 * 因此这里不需要（也不允许）去手动通知任何页面刷新。
 * ========================================================================= */

class SemesterViewModel(
    private val semesterRepository: SemesterRepository
) : ViewModel() {

    private val switchingTo = MutableStateFlow<Long?>(null)
    private val switchError = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<SemesterUiState> =
        combine(semesterRepository.observeAll(), switchingTo, switchError) { semesters, switching, error ->
            if (error != null && semesters.isEmpty()) {
                return@combine SemesterUiState.Error(error, retryable = true)
            }
            if (semesters.isEmpty()) {
                SemesterUiState.Empty
            } else {
                SemesterUiState.Content(
                    rows = semesters.map {
                        SemesterRow(
                            id = it.id,
                            name = it.name,
                            startDate = it.startDate,
                            totalWeeks = it.totalWeeks,
                            isCurrent = it.isCurrent
                        )
                    },
                    switchingTo = switching
                )
            }
        }
            .catch { throwable ->
                emit(SemesterUiState.Error(AppError.Unknown(throwable), retryable = true))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SemesterUiState.Loading
            )

    /**
     * 切换当前学期。
     * @param onDone 切换成功后的回调（用于返回今日课程页；编排动画由今日页承担）
     */
    fun switchTo(id: Long, onDone: () -> Unit = {}) {
        if (switchingTo.value != null) return
        viewModelScope.launch {
            switchingTo.value = id
            switchError.value = null
            when (val result = semesterRepository.switchTo(id)) {
                is AppResult.Success -> onDone()
                is AppResult.Failure -> switchError.value = result.error
            }
            switchingTo.value = null
        }
    }

    fun dismissError() {
        switchError.value = null
    }
}
