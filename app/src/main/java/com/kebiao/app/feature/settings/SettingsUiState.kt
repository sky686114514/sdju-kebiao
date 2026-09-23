package com.kebiao.app.feature.settings

import com.kebiao.app.core.AppError
import com.kebiao.app.data.settings.ReminderSettings
import com.kebiao.app.ui.theme.ThemeMode

/* =========================================================================
 * 设置 UiState（Spec §7 + UIUX §7.5）
 *
 * 深色模式只给「跟随系统 / 浅色 / 深色」三个显式选项，不重复发明系统开关，
 * 默认跟随系统（ThemeMode.System）。
 *
 * 提醒提前量档位锁定 5 / 10 / 15 / 20 / 30 分钟，默认 15（Spec D-7）。
 * 档位列表**直接引用 data 层的白名单常量**，避免 UI 与仓储各维护一份而漂移。
 * ========================================================================= */

data class SettingsUiState(
    val loading: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Int = ReminderSettings.DEFAULT_LEAD_MINUTES,
    val themeMode: ThemeMode = ThemeMode.System,
    /** UIUX §7.5「显示非本周课程」。开启后周视图以 55% + 虚线呈现未命中的单双周课。 */
    val showNonCurrentWeekCourses: Boolean = false,
    val error: AppError? = null
) {
    companion object {
        const val DEFAULT_LEAD_MINUTES: Int = ReminderSettings.DEFAULT_LEAD_MINUTES

        /** 提前量档位（分钟）。与 `ReminderSettingsRepository.setLeadMinutes` 的白名单同源。 */
        val LEAD_OPTIONS: List<Int> = ReminderSettings.ALLOWED_LEAD_MINUTES
    }
}
