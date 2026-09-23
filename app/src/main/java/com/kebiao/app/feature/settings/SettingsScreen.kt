package com.kebiao.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kebiao.app.ui.components.KebiaoGlassTopAppBar
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.ThemeMode

/* =========================================================================
 * 页面四：设置（UIUX §7.5）
 *
 * 用 1dp border 分组，不用卡片盒子。组内行高 48dp（Material 3 最小触控目标）。
 * 开关用 M3 原生 ripple（100ms）+ spring(Snappy) 滑块位移，不自定义动画。
 *
 * 深色模式不发明「跟随系统之外」的第三套色，但给浅色 / 深色两个显式选项
 * （用户明确要求设置页含主题项）。
 *
 * 行件（SettingsGroup / SwitchRow / ChoiceRow / ActionRow / InfoRow）在 SettingsRows.kt。
 * ========================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onReminderEnabledChange: (Boolean) -> Unit,
    onLeadMinutesChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onShowNonCurrentWeekChange: (Boolean) -> Unit,
    onOpenImport: () -> Unit,
    onOpenSemesters: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(contentPadding)) {
        KebiaoGlassTopAppBar(
            title = {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = KebiaoSpacing.X16)
        ) {
            SettingsGroup(title = "课表显示") {
                SwitchRow(
                    title = "显示非本周课程",
                    subtitle = "开启后，单双周未命中的课以 55% 淡显 + 虚线框出现在周视图",
                    checked = state.showNonCurrentWeekCourses,
                    onCheckedChange = onShowNonCurrentWeekChange
                )
            }

            SettingsGroup(title = "提醒") {
                SwitchRow(
                    title = "上课提醒",
                    subtitle = if (state.reminderEnabled) {
                        "每节课开始前 ${state.reminderLeadMinutes} 分钟通知你"
                    } else {
                        "关闭后早八不会收到通知"
                    },
                    checked = state.reminderEnabled,
                    onCheckedChange = onReminderEnabledChange
                )
                ChoiceRow(
                    title = "提前量",
                    options = SettingsUiState.LEAD_OPTIONS.map { it to "$it 分钟" },
                    selected = state.reminderLeadMinutes,
                    onSelect = onLeadMinutesChange
                )
            }

            SettingsGroup(title = "外观") {
                ChoiceRow(
                    title = "主题",
                    options = listOf(
                        ThemeMode.System to "跟随系统",
                        ThemeMode.Light to "浅色",
                        ThemeMode.Dark to "深色"
                    ),
                    selected = state.themeMode,
                    onSelect = onThemeModeChange
                )
            }

            SettingsGroup(title = "数据") {
                ActionRow(
                    icon = KebiaoIcons.FileDownload,
                    title = "从教务系统导入",
                    subtitle = "用学号登录，拉取最新课表",
                    onClick = onOpenImport
                )
                ActionRow(
                    icon = KebiaoIcons.SwapVert,
                    title = "学期管理",
                    subtitle = "切换或查看已导入的学期",
                    onClick = onOpenSemesters
                )
            }

            SettingsGroup(title = "关于") {
                InfoRow(title = "版本", value = "1.0.0 (MVP)")
                InfoRow(title = "数据来源", value = "上海电机学院教务系统")
                InfoRow(title = "存储位置", value = "仅本机，不上传任何服务器")
            }
        }
    }
}
