package com.kebiao.app.ui.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/* =========================================================================
 * 显示偏好（**UI 层自有**，与 data 层的 ReminderSettingsRepository 分立）
 *
 * 为什么要分两个文件而不是塞进一个设置仓储：
 *  - 「提醒」是产品行为（由 reminder 模块消费），不是显示偏好；
 *  - 「主题 / 是否显示非本周课程」只影响渲染，UI 层即可自洽，改它不应触碰提醒链路。
 * 两者共用 DataStore 机制，但**数据所有权与消费者不同**，因此分开。
 *
 * 存储纪律（与 data 层一致）：
 *  - 只存标量；**不在这里存"当前学期"**——那唯一存在 DB 的 is_current 列里（ADR-001）。
 * ========================================================================= */

data class DisplayPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    /** UIUX §7.5「显示非本周课程」：开启后单双周未命中的课以 55% + 虚线呈现。 */
    val showNonCurrentWeekCourses: Boolean = false,
)

private val Context.displayPreferencesStore: DataStore<Preferences> by
    preferencesDataStore(name = "display_preferences")

class DisplayPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.displayPreferencesStore)

    val flow: Flow<DisplayPreferences> = dataStore.data.map { prefs ->
        DisplayPreferences(
            themeMode = indexToThemeMode(prefs[KEY_THEME_MODE] ?: THEME_SYSTEM),
            showNonCurrentWeekCourses = prefs[KEY_SHOW_NON_CURRENT_WEEK] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = themeModeToIndex(mode) }
    }

    suspend fun setShowNonCurrentWeekCourses(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_NON_CURRENT_WEEK] = show }
    }

    /**
     * 越界值一律回落到 System，而不是取模或抛错：
     * 这是一个展示偏好，读到脏值时"跟随系统"是最不意外的结果。
     */
    private fun indexToThemeMode(index: Int): ThemeMode = when (index) {
        THEME_LIGHT -> ThemeMode.Light
        THEME_DARK -> ThemeMode.Dark
        else -> ThemeMode.System
    }

    private fun themeModeToIndex(mode: ThemeMode): Int = when (mode) {
        ThemeMode.System -> THEME_SYSTEM
        ThemeMode.Light -> THEME_LIGHT
        ThemeMode.Dark -> THEME_DARK
    }

    private companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        val KEY_THEME_MODE = intPreferencesKey("theme_mode_index")
        val KEY_SHOW_NON_CURRENT_WEEK = booleanPreferencesKey("show_non_current_week_courses")
    }
}
