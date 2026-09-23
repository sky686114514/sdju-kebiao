package com.kebiao.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kebiao.app.di.AppContainer
import com.kebiao.app.feature.settings.SettingsViewModel
import com.kebiao.app.ui.navigation.KebiaoNavHost
import com.kebiao.app.ui.prefs.DisplayPreferencesRepository
import com.kebiao.app.ui.theme.CoursePaletteAccentPaletteProvider
import com.kebiao.app.ui.theme.KebiaoTheme

/* =========================================================================
 * MainActivity —— **只装配与转发，零业务逻辑**（Spec 第 10 节 / code-organization 第 4 条）
 *
 * 三类动作，各一行：
 *  1. 开 edge-to-edge（内容延伸到系统栏下，配合 res/values/themes.xml 的透明系统栏）；
 *  2. 取依赖容器，把主题层的课程色板接到小组件端口（避免小组件退化为无色文本）；
 *  3. 装配「设置 ViewModel」并交给 [KebiaoNavHost] —— 主题需要主题模式，
 *     而主题模式是设置的产物，所以设置 VM 提升到 Activity 级（唯一被提升的状态）。
 *
 * 这里不出现任何日期计算、不查库、不判断"今天"，也不持有业务状态。
 * ========================================================================= */

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = AppContainer.from(this)
        val appContext: Context = applicationContext

        // 小组件识别色的接线（唯一一次）。若缺失，小组件不画识别环并写 error_occurred，
        // 因此这一步是"颜色规则有没有接上"的开关。
        container.accentPaletteProvider = CoursePaletteAccentPaletteProvider

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = settingsFactory(container, appContext)
            )
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

            KebiaoTheme(themeMode = settings.themeMode) {
                KebiaoNavHost(
                    container = container,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

/**
 * 设置 ViewModel 的工厂。
 *
 * 两个数据源刻意分开注入，因为在项目里它们是**两个所有者**：
 *  - 提醒开关 / 提前量 -> data 层 `ReminderSettingsRepository`（reminder 模块也消费）；
 *  - 主题 / 显示非本周课程 -> UI 层 `DisplayPreferencesRepository`（只影响渲染）。
 * 所以这里不能"图省事"把它们塞进同一个仓储。
 */
private fun settingsFactory(container: AppContainer, appContext: Context) = viewModelFactory {
    initializer {
        SettingsViewModel(
            reminderSettings = container.reminderSettingsRepository,
            displayPreferences = DisplayPreferencesRepository(appContext)
        )
    }
}
