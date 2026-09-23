@file:OptIn(ExperimentalTextApi::class)

package com.kebiao.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/* =========================================================================
 * 课刻 · 字体 Token（来源 docs/design-tokens.kt §3 + docs/UIUX.md §3.2）
 *
 * 中文字体：不打包，走 FontFamily.Default 交给系统（MiSans / HarmonyOS Sans /
 *           OPPO Sans / Noto Sans CJK）。硬塞第三方中文字体会绕过系统字号缩放。
 * 数字与拉丁：走等宽族并开启 tnum，保证时间轴 08:10 / 10:00 / 12:30 竖向对齐。
 *
 * 降级链（UIUX §3.2）：
 *   1. Inter variable latin-subset（约 30KB，Spec D-3 批准打包）
 *   2. Inter 未打包 -> FontFamily.Monospace
 *   3. 等宽不可用 -> FontFamily.Default
 *
 * 当前落地 = 第 2 级（Monospace）。res/font/inter_variable.ttf 就位后，
 * 只需把 NumericFamily 换成 FontFamily(Font(R.font.inter_variable, ...))，
 * 其余代码无需改动。刻意不写 Font(R.font.inter_variable) 以免引用不存在的资源导致编译失败。
 * ========================================================================= */

object KebiaoFonts {

    /** 拉丁 + 数字。见类注释的降级链说明。 */
    val NumericFamily: FontFamily = FontFamily.Monospace

    /** 未打包 Inter 时的降级链第 2 级（与 NumericFamily 同源，保留语义别名）。 */
    val NumericFallback: FontFamily = FontFamily.Monospace

    /** 中文与正文：交给系统，保证厂商字体一致性与系统字号缩放。 */
    val System: FontFamily = FontFamily.Default

    /** 时间、节次、教室编号、学时开启 tabular figures，保证竖向对齐。 */
    private const val NUMERIC_FEATURES = "tnum"

    fun numeric(size: Int, lineHeight: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = NumericFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = NUMERIC_FEATURES
    )
}

object KebiaoTypography {

    private fun cn(size: Int, lineHeight: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = KebiaoFonts.System,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )

    val Current: Typography = Typography(
        // 大数字：今天几门课、本周总课时。M3 headlineLarge。
        headlineLarge = KebiaoFonts.numeric(32, 40, FontWeight.SemiBold, -0.02),
        // 页面主标题：今天 / 本周课表
        headlineMedium = cn(28, 34, FontWeight.SemiBold, -0.02),
        headlineSmall = cn(22, 28, FontWeight.SemiBold, -0.01),

        // 学期名、区块标题
        titleLarge = cn(22, 28, FontWeight.SemiBold, -0.01),
        // 课程名（卡片主标题）
        titleMedium = cn(18, 24, FontWeight.SemiBold, -0.005),
        // 小节标题、状态 pill 文字
        titleSmall = cn(16, 22, FontWeight.Medium),

        // 正文
        bodyLarge = cn(16, 24, FontWeight.Normal),
        // 教室、教师、辅助说明
        bodyMedium = cn(14, 20, FontWeight.Normal),
        // 元数据、周次标注
        bodySmall = cn(12, 16, FontWeight.Normal, 0.02),

        // 时间标签、chip
        labelLarge = cn(13, 18, FontWeight.Medium, 0.01),
        labelMedium = cn(12, 16, FontWeight.Medium, 0.01),
        // 全大写 overline（仅 Widget 的 TODAY 标签用，letter-spacing >= 0.06em）
        labelSmall = cn(11, 14, FontWeight.SemiBold, 0.08)
    )
}

/** 供 UI 直接调用的语义别名，含数字专用样式（等宽对齐）。 */
object AppType {

    /** 时间 08:10-09:40 / 节次 1-2 节 / 教室 B103。 */
    @Composable @ReadOnlyComposable
    fun time() = MaterialTheme.typography.labelLarge.copy(
        fontFamily = KebiaoFonts.NumericFamily,
        fontFeatureSettings = "tnum"
    )

    /** 时间轴左侧的节次起止时间，需要严格竖向对齐。 */
    @Composable @ReadOnlyComposable
    fun axisTime() = MaterialTheme.typography.bodySmall.copy(
        fontFamily = KebiaoFonts.NumericFamily,
        fontFeatureSettings = "tnum"
    )

    /** 大数字统计。 */
    @Composable @ReadOnlyComposable
    fun stat() = MaterialTheme.typography.headlineLarge

    /** 课程名。 */
    @Composable @ReadOnlyComposable
    fun courseName() = MaterialTheme.typography.titleMedium

    /** 地点与教师行。 */
    @Composable @ReadOnlyComposable
    fun meta() = MaterialTheme.typography.bodyMedium
}
