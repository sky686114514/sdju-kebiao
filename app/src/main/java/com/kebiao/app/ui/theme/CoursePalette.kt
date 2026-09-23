package com.kebiao.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* =========================================================================
 * 课程识别色卡（8 色）· 来源 docs/design-tokens.kt §2
 *
 * 设计要点：8 色共享相近明度 + 中等彩度，只变色相且色相刻意分离
 * （216 / 188 / 152 / 72 / 38 / 18 / 330 / 268）。
 *
 * 铁律（违反即退回）：
 *  1. 一色一课：课程代码稳定哈希取模 8，全 App 与 Widget 永远同色。
 *  2. 色只出现在 8dp 圆点 + 约 10% 面积的低饱和 tint 底，绝不整格铺满。
 *  3. 课程名文字永远中性色，对比度恒定。
 *  4. 禁止 border-left 色条（本项目违规写法）。
 * ========================================================================= */

/** 单个课程色：value 用于圆点，tint 用于卡片/标签背景。 */
data class CourseColor(
    val id: Int,
    val name: String,
    val hue: Int,
    val value: Color,
    val tint: Color
)

object CoursePalette {

    val Light: List<CourseColor> = listOf(
        CourseColor(0, "蓝", 216, Color(0xFF1F63D6), Color(0xFFE8EFFC)),
        CourseColor(1, "青", 188, Color(0xFF0B7C8C), Color(0xFFE2F1F3)),
        CourseColor(2, "绿", 152, Color(0xFF0F7A48), Color(0xFFE2F2EA)),
        CourseColor(3, "橄榄", 72, Color(0xFF6B7A0F), Color(0xFFEEF1DF)),
        CourseColor(4, "琥珀", 38, Color(0xFF9A6206), Color(0xFFF8EEDD)),
        CourseColor(5, "橙红", 18, Color(0xFFB94A1A), Color(0xFFFAE9E1)),
        CourseColor(6, "品红", 330, Color(0xFFAE3A78), Color(0xFFF9E6F0)),
        CourseColor(7, "紫", 268, Color(0xFF6E4FD8), Color(0xFFEEEAFB))
    )

    val Dark: List<CourseColor> = listOf(
        CourseColor(0, "蓝", 216, Color(0xFF7CA6FF), Color(0xFF182742)),
        CourseColor(1, "青", 188, Color(0xFF4FC0D0), Color(0xFF122C2F)),
        CourseColor(2, "绿", 152, Color(0xFF59C48E), Color(0xFF142E22)),
        CourseColor(3, "橄榄", 72, Color(0xFFB3C24D), Color(0xFF272B17)),
        CourseColor(4, "琥珀", 38, Color(0xFFE2B055), Color(0xFF332617)),
        CourseColor(5, "橙红", 18, Color(0xFFF0885A), Color(0xFF37211A)),
        CourseColor(6, "品红", 330, Color(0xFFF085BC), Color(0xFF37202C)),
        CourseColor(7, "紫", 268, Color(0xFFA88CFA), Color(0xFF292444))
    )

    /** 当前主题下的色卡，由 KebiaoTheme 注入。 */
    val current: List<CourseColor>
        @Composable @ReadOnlyComposable get() = LocalCoursePalette.current

    /**
     * 索引归一化。索引的**权威来源是 domain 的 `Course.accentIndex`**
     * （FNV-1a 稳定哈希取模 8，见 `domain/model/Course.kt`），
     * 主题层只负责「索引 -> 颜色」这一步，绝不自己再算一次哈希。
     *
     * 为什么不在 UI 层重算：小组件（`widget/AccentPaletteProvider`）拿到的也是
     * `accentIndex`，两处若各写一套哈希，同一门课会在课表与小组件里显示不同颜色
     * ——这正是本项目点名禁止的「沉默逻辑错误」。
     */
    fun normalizeIndex(accentIndex: Int): Int = ((accentIndex % 8) + 8) % 8

    /** 用 domain 的 `accentIndex` 取色（非 Composable 场景：小组件、预览、单测）。 */
    fun forIndex(accentIndex: Int, dark: Boolean = false): CourseColor =
        (if (dark) Dark else Light)[normalizeIndex(accentIndex)]

    /** 8 色板按 `accentIndex` 顺序的纯色值，供 Glance 小组件使用（只允许出现在主题层）。 */
    fun valuesInIndexOrder(): List<Color> = Light.map { it.value }
}

/**
 * 当前主题下的课程色。组件一律用这个，不自己判断明暗 —— 明暗由 KebiaoTheme 注入，
 * 与 ThemeMode 保持一致（跟随系统 / 强制浅色 / 强制深色）。
 *
 * @param accentIndex 来自 [com.kebiao.app.domain.model.SessionOccurrence.accentIndex]
 */
@Composable
@ReadOnlyComposable
fun courseColorFor(accentIndex: Int): CourseColor =
    LocalCoursePalette.current[CoursePalette.normalizeIndex(accentIndex)]

val LocalCoursePalette = staticCompositionLocalOf { CoursePalette.Light }
