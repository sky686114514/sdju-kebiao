package com.kebiao.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 提醒设置。
 *
 * 只存标量（Spec 第 4.2 节：DataStore 仅用于提醒提前量与主题偏好），
 * **不存"当前学期"** —— 那唯一存在 DB 的 `is_current` 列里，避免双真相源（ADR-001）。
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    /** 提前量，单位分钟。允许 5 / 10 / 15 / 20 / 30（总监裁决 D-7）。 */
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    /** 可选的每日早课汇总通知（ARCHITECTURE.md 第 8.2 节第 5 条）。 */
    val dailySummaryEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_LEAD_MINUTES: Int = 15

        /** 允许的提前量档位（D-7：用户可改，5/10/15/20/30 分钟）。 */
        val ALLOWED_LEAD_MINUTES: List<Int> = listOf(5, 10, 15, 20, 30)
    }
}

private val Context.reminderSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_settings")

class ReminderSettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.reminderSettingsStore)

    val flow: Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[KEY_ENABLED] ?: true,
            leadMinutes = prefs[KEY_LEAD_MINUTES] ?: ReminderSettings.DEFAULT_LEAD_MINUTES,
            dailySummaryEnabled = prefs[KEY_DAILY_SUMMARY] ?: false,
        )
    }

    suspend fun current(): ReminderSettings = flow.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    /** 只接受 [ReminderSettings.ALLOWED_LEAD_MINUTES] 内的档位；其它值直接拒绝而不是悄悄取整。 */
    suspend fun setLeadMinutes(minutes: Int) {
        require(minutes in ReminderSettings.ALLOWED_LEAD_MINUTES) {
            "提前量只允许 ${ReminderSettings.ALLOWED_LEAD_MINUTES.joinToString("/")} 分钟，实得 $minutes"
        }
        dataStore.edit { it[KEY_LEAD_MINUTES] = minutes }
    }

    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DAILY_SUMMARY] = enabled }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_LEAD_MINUTES = intPreferencesKey("reminder_lead_minutes")
        val KEY_DAILY_SUMMARY = booleanPreferencesKey("reminder_daily_summary")
    }
}
