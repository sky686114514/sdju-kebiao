package com.kebiao.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.core.AppError
import com.kebiao.app.data.settings.ReminderSettings
import com.kebiao.app.data.settings.ReminderSettingsRepository
import com.kebiao.app.ui.prefs.DisplayPreferencesRepository
import com.kebiao.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* =========================================================================
 * 设置 ViewModel
 *
 * 两个数据源，各自的写入通道唯一：
 *  - 提醒开关 / 提前量 -> [ReminderSettingsRepository]（data 层，reminder 模块同样消费）
 *  - 主题 / 显示非本周课程 -> [DisplayPreferencesRepository]（ui 层，只影响渲染）
 *
 * 读用 Flow 订阅，写用 suspend setter —— 没有本地缓存副本，也就不存在
 * 「改了没生效」的第二真相源。
 * ========================================================================= */

class SettingsViewModel(
    private val reminderSettings: ReminderSettingsRepository,
    private val displayPreferences: DisplayPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(reminderSettings.flow, displayPreferences.flow) { reminder, display ->
            SettingsUiState(
                loading = false,
                reminderEnabled = reminder.enabled,
                reminderLeadMinutes = reminder.leadMinutes,
                themeMode = display.themeMode,
                showNonCurrentWeekCourses = display.showNonCurrentWeekCourses
            )
        }
            .catch { throwable ->
                emit(SettingsUiState(loading = false, error = AppError.Unknown(throwable)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState()
            )

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { reminderSettings.setEnabled(enabled) }
    }

    /**
     * 只接受 5 / 10 / 15 / 20 / 30 这五个档位（越权值直接忽略）。
     * 白名单来自 data 层 [ReminderSettings.ALLOWED_LEAD_MINUTES]，
     * 与 `ReminderSettingsRepository.setLeadMinutes` 内部的 require 同源，不重复维护常量。
     */
    fun setReminderLeadMinutes(minutes: Int) {
        if (minutes !in ReminderSettings.ALLOWED_LEAD_MINUTES) return
        viewModelScope.launch { reminderSettings.setLeadMinutes(minutes) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { displayPreferences.setThemeMode(mode) }
    }

    fun setShowNonCurrentWeekCourses(show: Boolean) {
        viewModelScope.launch { displayPreferences.setShowNonCurrentWeekCourses(show) }
    }
}
