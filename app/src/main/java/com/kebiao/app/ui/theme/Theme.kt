package com.kebiao.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/* =========================================================================
 * 课刻 · 主题入口
 *
 * 刻意不启用 Material You 动态取色：课程色卡需要 8 色之间保持可分辨距离，
 * 由壁纸取色会破坏这个距离，也会让不同设备上的课表观感漂移。
 *
 * 主题模式：跟随系统（M3 规范与 Android 平台惯例）。App 内不重复发明开关。
 * ========================================================================= */

/** 主题模式。当前 MVP 只暴露跟随系统；预留 Light/Dark 供后续设置页扩展。 */
enum class ThemeMode { System, Light, Dark }

@Composable
fun KebiaoTheme(
    themeMode: ThemeMode = ThemeMode.System,
    motionEnvironment: KebiaoMotionEnvironment? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val colorScheme = if (darkTheme) KebiaoColorScheme.Dark else KebiaoColorScheme.Light
    val coursePalette = if (darkTheme) CoursePalette.Dark else CoursePalette.Light
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val accentTint = AccentTint(if (darkTheme) Color(0xFF16243A) else Color(0xFFE7F0FE))

    val context = LocalContext.current
    // 只解析一次；reduced motion 变化需要重启 Activity 才能被系统反映，属平台既定行为
    val motion = motionEnvironment ?: remember(context) { KebiaoAccessibility.resolve(context) }

    CompositionLocalProvider(
        LocalCoursePalette provides coursePalette,
        LocalAccentTint provides accentTint,
        LocalSemanticColors provides semanticColors,
        LocalMotionEnvironment provides motion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KebiaoTypography.Current,
            shapes = KebiaoShapes.Current,
            content = content
        )
    }
}
