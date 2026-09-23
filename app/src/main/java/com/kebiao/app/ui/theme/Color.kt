package com.kebiao.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* =========================================================================
 * 课刻 · 颜色 Token（唯一色值来源）
 *
 * 来源：docs/design-tokens.kt §1 + docs/UIUX.md §3.1
 * 本文件是 D-1 裁决豁免的「Token 定义文件」，允许出现字面色值。
 * 组件与页面代码一律通过 MaterialTheme.colorScheme / LocalCoursePalette /
 * LocalAccentTint / LocalSemanticColors 引用，禁止裸 hex。
 *
 * 设计方向：冷色中性底 + 单一强调色（电控蓝 #1D6FE8）+ 8 色课程识别环。
 * 明确排除：紫到粉渐变、玻璃拟态、纯黑 OLED。
 * ========================================================================= */

object KebiaoColorScheme {

    val Light: ColorScheme = lightColorScheme(
        // accent 角色：电控蓝，色相 216，对白底 4.67:1（WCAG AA）
        primary = Color(0xFF1D6FE8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E6FD),
        onPrimaryContainer = Color(0xFF0B3B85),
        inversePrimary = Color(0xFF6FA8FF),

        // 次强调：本 App 不用于装饰，只用于图例与次级标记
        secondary = Color(0xFF4A5568),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDF0F6),
        onSecondaryContainer = Color(0xFF2C313A),

        // 第三强调：保留 M3 槽位，映射到青，用于线上课等类目标记
        tertiary = Color(0xFF0B7C8C),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE2F1F3),
        onTertiaryContainer = Color(0xFF03444E),

        background = Color(0xFFF4F6FA),
        onBackground = Color(0xFF171A21),

        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF171A21),
        surfaceVariant = Color(0xFFE6EBF5),
        onSurfaceVariant = Color(0xFF59616E),
        surfaceTint = Color(0xFF1D6FE8),

        inverseSurface = Color(0xFF2C313A),
        inverseOnSurface = Color(0xFFE8EBF1),

        error = Color(0xFFC62828),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE3E2),
        onErrorContainer = Color(0xFF5A1010),

        outline = Color(0xFFE2E6EE),
        outlineVariant = Color(0xFFEFF2F7),
        scrim = Color(0x700C121C),

        // Material3 1.2.0+ 新增槽位：深色层级与容器分层
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE6EBF5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8FAFD),
        surfaceContainer = Color(0xFFEDF0F6),
        surfaceContainerHigh = Color(0xFFE6EBF5),
        surfaceContainerHighest = Color(0xFFDFE5F0)
    )

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF6FA8FF),
        onPrimary = Color(0xFF06152B),
        primaryContainer = Color(0xFF1D3050),
        onPrimaryContainer = Color(0xFFC9DEFF),
        inversePrimary = Color(0xFF1D6FE8),

        secondary = Color(0xFFA2ABB8),
        onSecondary = Color(0xFF131820),
        secondaryContainer = Color(0xFF1C2129),
        onSecondaryContainer = Color(0xFFC7CDD8),

        tertiary = Color(0xFF4FC0D0),
        onTertiary = Color(0xFF06272C),
        tertiaryContainer = Color(0xFF122C2F),
        onTertiaryContainer = Color(0xFFB6E7EE),

        // 深色底不用纯黑；层级靠亮度递进而非阴影
        background = Color(0xFF0E1116),
        onBackground = Color(0xFFE8EBF1),

        surface = Color(0xFF161A21),
        onSurface = Color(0xFFE8EBF1),
        surfaceVariant = Color(0xFF242A34),
        onSurfaceVariant = Color(0xFFA2ABB8),
        surfaceTint = Color(0xFF6FA8FF),

        inverseSurface = Color(0xFFE8EBF1),
        inverseOnSurface = Color(0xFF1C2129),

        error = Color(0xFFF2706A),
        onError = Color(0xFF3A1A19),
        errorContainer = Color(0xFF3A1A19),
        onErrorContainer = Color(0xFFFBD5D3),

        outline = Color(0xFF2A313C),
        outlineVariant = Color(0xFF1E232B),
        scrim = Color(0x99000000),

        surfaceBright = Color(0xFF2A313C),
        surfaceDim = Color(0xFF0E1116),
        surfaceContainerLowest = Color(0xFF0A0D11),
        surfaceContainerLow = Color(0xFF12161C),
        surfaceContainer = Color(0xFF1C2129),
        surfaceContainerHigh = Color(0xFF242A34),
        surfaceContainerHighest = Color(0xFF2F3742)
    )
}

/**
 * 语义色。UIUX §3.1 定义，M3 ColorScheme 无对应槽位，单独承载。
 * warn 用偏深的金棕 #A16207 而非明黄：明黄在白底对比度仅 1.9:1，只能作状态点不能承载文字。
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warn: Color,
    val onWarn: Color,
    val info: Color,
    val borderSoft: Color,
    val skeletonBase: Color,
    val skeletonHighlight: Color,
    val focusRing: Color
)

val LightSemanticColors = SemanticColors(
    success = Color(0xFF14855A),
    onSuccess = Color(0xFFFFFFFF),
    warn = Color(0xFFA16207),
    onWarn = Color(0xFFFFFFFF),
    info = Color(0xFF1D6FE8),
    borderSoft = Color(0xFFE2E6EE),
    skeletonBase = Color(0xFFE6EBF5),
    skeletonHighlight = Color(0xFFF4F6FA),
    focusRing = Color(0x521D6FE8)
)

val DarkSemanticColors = SemanticColors(
    success = Color(0xFF4CC38A),
    onSuccess = Color(0xFF06251A),
    warn = Color(0xFFE0A63C),
    onWarn = Color(0xFF2A1C05),
    info = Color(0xFF6FA8FF),
    borderSoft = Color(0xFF2A313C),
    skeletonBase = Color(0xFF242A34),
    skeletonHighlight = Color(0xFF2F3742),
    focusRing = Color(0x616FA8FF)
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/**
 * 「进行中」卡片底色。UIUX §3.1 / §5.5：accent tint，浅 #E7F0FE / 深 #16243A。
 * 与 primaryContainer 刻意分开取值——primaryContainer 用于 Chip 选中态，更饱和。
 */
data class AccentTint(val value: Color)

val LocalAccentTint = staticCompositionLocalOf { AccentTint(Color(0xFFE7F0FE)) }

/** 非当前周的课程（单双周未命中）使用的降透明度。UIUX §7.1。 */
object Dimming {
    const val NOT_THIS_WEEK_ALPHA = 0.55f
}
