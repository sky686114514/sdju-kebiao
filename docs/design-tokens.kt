@file:OptIn(ExperimentalTextApi::class)

package com.sdju.kebiao.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor

/* =========================================================================
 * 课表 App 设计 Token（Jetpack Compose / Material 3）
 *
 * 来源：docs/design-tokens.json + docs/UIUX.md
 * 设计师：颜好看
 * 寄存器：product（设计服务产品）  平台轴：android（Material 3）
 * 三轴刻度：Variance 5 / Motion 7 / Density 5
 *
 * 使用：
 *   KebiaoTheme { App() }                        // 自动浅色 / 深色
 *   MaterialTheme.colorScheme.accent             // 见 CoursePalette 扩展
 *   MotionTokens.Duration.Medium                 // 动效时长
 *   MotionTokens.Recipe.SemesterSwitchEnter()     // 编排好的 transition
 *   KebiaoIcon(R.drawable.ic_schedule, "上课时间", size = IconSize.Inline)
 *
 * 硬约束（违反即退回）：
 *   1. 颜色只能来自本文件或 MaterialTheme.colorScheme，禁止裸 hex
 *   2. 图标只能用 Material Symbols（res/drawable/ic_*），禁止 emoji
 *   3. 动效只能用 MotionTokens，禁止过冲型（overshoot）与 elastic 曲线
 *   4. 字号只能用 sp，间距只能用 4dp 网格上列出的值
 * ========================================================================= */

/* ------------------------------------------------------------------ *
 * 1. ColorScheme（浅色 / 深色）
 * ------------------------------------------------------------------ */

object KebiaoColorScheme {

    val Light: ColorScheme = lightColorScheme(
        // accent 角色
        primary = Color(0xFF1D6FE8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E6FD),
        onPrimaryContainer = Color(0xFF0B3B85),
        inversePrimary = Color(0xFF6FA8FF),

        // 次强调（本 App 不用于装饰，只用于图例与次级标记）
        secondary = Color(0xFF4A5568),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDF0F6),
        onSecondaryContainer = Color(0xFF2C313A),

        // 第三强调（保留 M3 槽位，映射到青，用于线上课等类目标记）
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

/* ------------------------------------------------------------------ *
 * 2. 课程识别色卡（8 色）
 *
 * 设计要点：8 色共享相近明度 + 中等彩度，只变色相且色相分离
 * （216 / 188 / 152 / 72 / 38 / 18 / 330 / 268）。
 * 只出现在 8dp 圆点与约 10% 面积的低饱和 tint 底上，绝不整格铺满。
 * 分配规则：课程代码稳定哈希取模 8，同一门课全 App 与 Widget 永远同色。
 * ------------------------------------------------------------------ */

/** 单个课程色：主色用于圆点与文字，tint 用于卡片/标签背景。 */
data class CourseColor(val id: Int, val name: String, val hue: Int, val value: Color, val tint: Color)

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

    /** 用课程代码稳定取色。同一门课在任何页面与 Widget 都是同一色。 */
    fun forCode(courseCode: String, dark: Boolean = false): CourseColor {
        val table = if (dark) Dark else Light
        var hash = 0
        for (ch in courseCode) {
            hash = hash * 31 + ch.code
        }
        val idx = ((hash % table.size) + table.size) % table.size
        return table[idx]
    }
}

val LocalCoursePalette = staticCompositionLocalOf { CoursePalette.Light }

/* ------------------------------------------------------------------ *
 * 3. Typography
 *
 * 中文走系统字体（FontFamily.Default 交给系统 Noto Sans CJK / 厂商字体），
 * 不打包中文字体文件。数字与拉丁走 Inter 拉丁子集 + tnum 等宽数字，
 * 保证 08:10 / 10:00 / 12:30 在时间轴上竖向对齐。
 * 落地位于 res/font/inter_variable.ttf（可选）。若未打包，NumericFamily
 * 自动降级到 FontFamily.Monospace，再降级到 FontFamily.Default。
 * ------------------------------------------------------------------ */

object KebiaoFonts {

    /** 拉丁 + 数字。需要 Compose UI 1.5+ 的 variationSettings 参数。 */
    val NumericFamily: FontFamily = FontFamily(
        Font(
            resId = com.sdju.kebiao.R.font.inter_variable,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            resId = com.sdju.kebiao.R.font.inter_variable,
            weight = FontWeight.Medium,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            resId = com.sdju.kebiao.R.font.inter_variable,
            weight = FontWeight.SemiBold,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))
        )
    )

    /** 未打包 Inter 时的降级链第 2 级。 */
    val NumericFallback: FontFamily = FontFamily.Monospace

    /** 中文与正文：交给系统，保证厂商字体一致性与系统字号缩放。 */
    val System: FontFamily = FontFamily.Default

    private val numericFeatures = "tnum"

    /** 时间、节次、教室编号、学时。开启 tabular figures。 */
    fun numeric(size: Int, lineHeight: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = NumericFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = numericFeatures
    )
}

object KebiaoTypography {

    /** 中文字体（标题 / 正文 / 标签），全部走系统字体栈。 */
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

/* ------------------------------------------------------------------ *
 * 4. Shapes
 * ------------------------------------------------------------------ */

object KebiaoShapes {

    val Current: Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp)
    )

    val Pill = RoundedCornerShape(999.dp)
}

/* ------------------------------------------------------------------ *
 * 5. Spacing（4dp 基准网格，禁用 5 / 7 / 13 / 15 / 22 / 30）
 * ------------------------------------------------------------------ */

object KebiaoSpacing {
    val None: Dp = 0.dp
    val X1: Dp = 4.dp
    val X2: Dp = 8.dp
    val X3: Dp = 12.dp
    val X4: Dp = 16.dp
    val X5: Dp = 20.dp
    val X6: Dp = 24.dp
    val X8: Dp = 32.dp
    val X10: Dp = 40.dp
    val X12: Dp = 48.dp
    val X16: Dp = 64.dp

    /** 语义角色 */
    val ScreenGutter: Dp = 20.dp
    val CardPadding: Dp = 16.dp
    val CardGap: Dp = 12.dp
    val InlineIconGap: Dp = 4.dp
    val BlockGap: Dp = 24.dp
    val SectionGap: Dp = 32.dp

    /** Material 3 最小触控目标。 */
    val TouchTarget: Dp = 48.dp
}

/* ------------------------------------------------------------------ *
 * 6. Elevation
 * ------------------------------------------------------------------ */

object KebiaoElevation {
    val Flat: Dp = 0.dp
    /** 默认卡片：1dp 边框，无阴影（Hairline First）。 */
    val Ring: Dp = 1.dp
    /** 悬浮元素：模糊 <= 8dp，且不加边框（避免幽灵卡片）。 */
    val Raised: Dp = 2.dp
    /** 弹窗、BottomSheet：只用阴影不加边框。 */
    val Overlay: Dp = 8.dp
}

/* ------------------------------------------------------------------ *
 * 7. MotionTokens（本 App 的动效契约）
 *
 * 全项目禁用过冲型（overshoot）缓动：三次贝塞尔控制点 y 值超出 0 到 1 区间的
 * 曲线，以及任何 elastic 曲线。全部弹簧 dampingRatio = 1.0f（NoBouncy）。
 * 所有 spring 的 dampingRatio 恒为 1.0（NoBouncy），数学上不存在回弹。
 * ------------------------------------------------------------------ */

object MotionTokens {

    /** 时长阶梯（毫秒）。150 是全项目收敛基准值。 */
    object Duration {
        const val Instant = 100
        const val Fast = 150
        const val Medium = 250
        const val Slow = 350
        const val Longer = 500
    }

    /** 缓动曲线。 */
    object Ease {
        val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        val Decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
        val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

        /** 骨架屏高光扫过，唯一允许的线性缓动。 */
        val Linear: Easing = CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)

        val Default: Easing = Standard
    }

    /** 弹簧参数。dampingRatio 必须为 DampingRatioNoBouncy。 */
    object Spring {
        val Snappy: FiniteAnimationSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        val Spatial: FiniteAnimationSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        val Gentle: FiniteAnimationSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

        val SnappyDp: FiniteAnimationSpec<Dp> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        val SpatialDp: FiniteAnimationSpec<Dp> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        val GentleDp: FiniteAnimationSpec<Dp> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    }

    /** 全局硬约束。 */
    object Rules {
        /** 同时运行的动画元素上限。 */
        const val MaxConcurrentAnimatedElements = 3
        /** 卡片入场动画只在首屏静止时播一次，滚动期间 0 动画。 */
        const val ListItemStaggerDelayMs = 60
        const val ListItemStaggerCap = 6
        const val ListItemStaggerDurationMs = 180
        /** 脉冲周期（进行中状态）。 */
        const val PulsePeriodMs = 1600
    }

    /**
     * 编排好的动画配方。UI 层直接调用，不自己写时长与曲线。
     */
    object Recipe {

        /* --- 页面切换：底部导航同级页（今日课程 / 周视图 / 设置） --- */
        fun fadeThroughEnter(): EnterTransition =
            fadeIn(tween(Duration.Medium, easing = Ease.Decelerate)) +
                scaleIn(tween(Duration.Medium, easing = Ease.Decelerate), initialScale = 0.94f)

        fun fadeThroughExit(): ExitTransition =
            fadeOut(tween(90, easing = Ease.Accelerate))

        /* --- 页面切换：层级推入（课程详情 / 学期管理 / 设置子页） --- */
        fun pushEnter(): EnterTransition =
            slideInHorizontally(tween(Duration.Slow, easing = Ease.Emphasized)) { it / 4 } +
                fadeIn(tween(Duration.Slow, easing = Ease.Decelerate))

        fun pushExit(): ExitTransition =
            slideOutHorizontally(tween(Duration.Slow, easing = Ease.Emphasized)) { -it / 6 } +
                fadeOut(tween(Duration.Slow, easing = Ease.Accelerate))

        /** 返回时把 enter / exit 互换并取反 offset，避免方向误读。 */
        fun popEnter(): EnterTransition =
            slideInHorizontally(tween(Duration.Slow, easing = Ease.Emphasized)) { -it / 6 } +
                fadeIn(tween(Duration.Slow, easing = Ease.Decelerate))

        fun popExit(): ExitTransition =
            slideOutHorizontally(tween(Duration.Slow, easing = Ease.Emphasized)) { it / 4 } +
                fadeOut(tween(Duration.Slow, easing = Ease.Accelerate))

        /* --- 内容更新：切换学期时旧列表退场（140ms，比入场快约 2 倍） --- */
        fun staleListExit(): ExitTransition =
            fadeOut(tween(140, easing = Ease.Accelerate)) +
                slideOutVertically(tween(140, easing = Ease.Accelerate)) { -it / 24 }

        /* --- 列表项入场（配合 stagger 延迟使用） --- */
        fun listItemEnter(staggerDelayMs: Int = 0): EnterTransition =
            fadeIn(tween(Rules.ListItemStaggerDurationMs, easing = Ease.Decelerate, delayMillis = staggerDelayMs)) +
                slideInVertically(
                    tween(Rules.ListItemStaggerDurationMs, easing = Ease.Decelerate, delayMillis = staggerDelayMs)
                ) { it / 6 }

        /** 封顶后的错峰延迟：index 超过 6 之后不再累加。 */
        fun staggerDelayFor(index: Int): Int =
            index.coerceAtMost(Rules.ListItemStaggerCap) * Rules.ListItemStaggerDelayMs

        /* --- 空状态：从有课变今天没课 --- */
        fun emptyStateIconEnter(): EnterTransition =
            scaleIn(tween(260, easing = Ease.Emphasized), initialScale = 0.88f) +
                fadeIn(tween(260, easing = Ease.Decelerate))

        fun emptyStateTextEnter(): EnterTransition =
            slideInVertically(tween(260, easing = Ease.Decelerate, delayMillis = 160)) { it / 8 } +
                fadeIn(tween(260, easing = Ease.Decelerate, delayMillis = 160))

        /* --- 周视图：回到本周按钮 --- */
        fun backToThisWeekEnter(): EnterTransition =
            scaleIn(tween(200, easing = Ease.Emphasized), initialScale = 0.8f) +
                fadeIn(tween(200, easing = Ease.Decelerate))

        fun backToThisWeekExit(): ExitTransition =
            scaleOut(tween(120, easing = Ease.Accelerate), targetScale = 0.8f) +
                fadeOut(tween(120, easing = Ease.Accelerate))

        /* --- 状态 pill：进行中标签横向展开，不抢视线 --- */
        fun statusPillEnter(): EnterTransition =
            expandHorizontally(tween(200, easing = Ease.Emphasized)) +
                fadeIn(tween(200, easing = Ease.Decelerate, delayMillis = 100))

        /* --- BottomSheet --- */
        fun sheetEnter(): EnterTransition =
            slideInVertically(tween(Duration.Slow, easing = Ease.Emphasized)) { it }

        fun sheetExit(): ExitTransition =
            slideOutVertically(tween(200, easing = Ease.Accelerate)) { it }

        /* --- 刷新完成：新出现的卡片 --- */
        fun refreshedItemEnter(): EnterTransition =
            fadeIn(tween(180, easing = Ease.Decelerate)) +
                scaleIn(tween(180, easing = Ease.Decelerate), initialScale = 0.96f)

        /* --- 状态切换：颜色过渡（即将开始 -> 进行中） --- */
        fun <T> stateChange(): FiniteAnimationSpec<T> = tween(300, easing = Ease.Standard)

        fun <T> quickColorChange(): FiniteAnimationSpec<T> = tween(200, easing = Ease.Standard)
    }
}

/* ------------------------------------------------------------------ *
 * 8. 图标（Material Symbols，全项目唯一一套，禁止 emoji）
 *
 * 接入方式：把 material-symbols 的 SVG 用 Android Studio 的
 * Vector Asset 导入为 res/drawable/ic_<snake_case>.xml。
 * 默认 FILL=0（Outlined），选中态用 FILL=1；两种状态各存一个 drawable，
 * 命名后缀 _fill。24dp 网格，字形固有线宽等效 2dp。
 * ------------------------------------------------------------------ */

object IconSize {
    /** 16dp：与文字同行的小标识。 */
    val Inline: Dp = 16.dp
    /** 20dp：紧凑行内控件与列表项尾随。 */
    val Compact: Dp = 20.dp
    /** 24dp：图标按钮、导航项、空状态、FAB（Material 3 标准）。 */
    val Standard: Dp = 24.dp
}

/**
 * 全 App 图标清单。左侧是业务语义，右侧是对应的 Material Symbols 名与 drawable 资源。
 * 新增 UI 元素时先在这个表里查找，不允许引入表外图标库。
 */
object KebiaoIcons {

    // 导航
    @DrawableRes val CalendarToday = com.sdju.kebiao.R.drawable.ic_calendar_today
    @DrawableRes val CalendarTodayFill = com.sdju.kebiao.R.drawable.ic_calendar_today_fill
    @DrawableRes val CalendarViewWeek = com.sdju.kebiao.R.drawable.ic_calendar_view_week
    @DrawableRes val CalendarViewWeekFill = com.sdju.kebiao.R.drawable.ic_calendar_view_week_fill
    @DrawableRes val Settings = com.sdju.kebiao.R.drawable.ic_settings
    @DrawableRes val SettingsFill = com.sdju.kebiao.R.drawable.ic_settings_fill
    @DrawableRes val ArrowBack = com.sdju.kebiao.R.drawable.ic_arrow_back
    @DrawableRes val MoreVert = com.sdju.kebiao.R.drawable.ic_more_vert
    @DrawableRes val ChevronRight = com.sdju.kebiao.R.drawable.ic_chevron_right

    // 课程卡
    @DrawableRes val Schedule = com.sdju.kebiao.R.drawable.ic_schedule
    @DrawableRes val LocationOn = com.sdju.kebiao.R.drawable.ic_location_on
    @DrawableRes val MeetingRoom = com.sdju.kebiao.R.drawable.ic_meeting_room
    @DrawableRes val Person = com.sdju.kebiao.R.drawable.ic_person
    @DrawableRes val School = com.sdju.kebiao.R.drawable.ic_school
    @DrawableRes val Timelapse = com.sdju.kebiao.R.drawable.ic_timelapse
    @DrawableRes val Tag = com.sdju.kebiao.R.drawable.ic_tag
    @DrawableRes val Wifi = com.sdju.kebiao.R.drawable.ic_wifi
    @DrawableRes val Help = com.sdju.kebiao.R.drawable.ic_help
    @DrawableRes val EventRepeat = com.sdju.kebiao.R.drawable.ic_event_repeat

    // 课程状态
    @DrawableRes val CheckCircle = com.sdju.kebiao.R.drawable.ic_check_circle
    @DrawableRes val RadioButtonChecked = com.sdju.kebiao.R.drawable.ic_radio_button_checked
    @DrawableRes val Upcoming = com.sdju.kebiao.R.drawable.ic_upcoming
    @DrawableRes val EventBusy = com.sdju.kebiao.R.drawable.ic_event_busy
    @DrawableRes val EventAvailable = com.sdju.kebiao.R.drawable.ic_event_available
    @DrawableRes val Warning = com.sdju.kebiao.R.drawable.ic_warning

    // 操作
    @DrawableRes val Refresh = com.sdju.kebiao.R.drawable.ic_refresh
    @DrawableRes val SwapVert = com.sdju.kebiao.R.drawable.ic_swap_vert
    @DrawableRes val FileDownload = com.sdju.kebiao.R.drawable.ic_file_download
    @DrawableRes val FileUpload = com.sdju.kebiao.R.drawable.ic_file_upload
    @DrawableRes val Add = com.sdju.kebiao.R.drawable.ic_add
    @DrawableRes val Edit = com.sdju.kebiao.R.drawable.ic_edit
    @DrawableRes val Delete = com.sdju.kebiao.R.drawable.ic_delete
    @DrawableRes val Search = com.sdju.kebiao.R.drawable.ic_search
    @DrawableRes val FilterList = com.sdju.kebiao.R.drawable.ic_filter_list
    @DrawableRes val Share = com.sdju.kebiao.R.drawable.ic_share
    @DrawableRes val Close = com.sdju.kebiao.R.drawable.ic_close
    @DrawableRes val ExpandMore = com.sdju.kebiao.R.drawable.ic_expand_more
    @DrawableRes val ExpandLess = com.sdju.kebiao.R.drawable.ic_expand_less

    // 提醒与小组件
    @DrawableRes val NotificationsActive = com.sdju.kebiao.R.drawable.ic_notifications_active
    @DrawableRes val NotificationsOff = com.sdju.kebiao.R.drawable.ic_notifications_off
    @DrawableRes val Alarm = com.sdju.kebiao.R.drawable.ic_alarm
    @DrawableRes val Widgets = com.sdju.kebiao.R.drawable.ic_widgets

    // 时间与周次
    @DrawableRes val ChevronLeft = com.sdju.kebiao.R.drawable.ic_chevron_left
    @DrawableRes val History = com.sdju.kebiao.R.drawable.ic_history
    @DrawableRes val DateRange = com.sdju.kebiao.R.drawable.ic_date_range
    @DrawableRes val Today = com.sdju.kebiao.R.drawable.ic_today

    // 空状态与错误
    @DrawableRes val Inbox = com.sdju.kebiao.R.drawable.ic_inbox
    @DrawableRes val CloudOff = com.sdju.kebiao.R.drawable.ic_cloud_off
    @DrawableRes val Error = com.sdju.kebiao.R.drawable.ic_error
    @DrawableRes val Lock = com.sdju.kebiao.R.drawable.ic_lock
    @DrawableRes val WifiOff = com.sdju.kebiao.R.drawable.ic_wifi_off
}

/** 统一图标入口。尺寸只能用 IconSize 的三档，tint 默认跟随内容色。 */
@Composable
fun KebiaoIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.Standard,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

/** 由 ImageVector 直接渲染（若前端选择用代码生成图标而非 drawable）。 */
@Composable
fun KebiaoIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.Standard,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

/* ------------------------------------------------------------------ *
 * 9. 无障碍与降级
 * ------------------------------------------------------------------ */

object KebiaoAccessibility {

    /**
     * 系统关闭动画（开发者选项 / 无障碍里的减少动画）。
     * 为真时：关闭 stagger 与脉冲，进退场直接到终态。
     */
    fun isReducedMotion(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale == 0f
    }

    /** 低内存设备：关闭脉冲与 stagger，spring 全部退化为 tween(200, Decelerate)。 */
    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        return am?.isLowRamDevice == true
    }
}

/* ------------------------------------------------------------------ *
 * 10. Theme
 * ------------------------------------------------------------------ */

/**
 * 主题入口。
 *
 * 刻意不启用 Material You 动态取色：课程色卡需要 8 色之间保持可分辨距离，
 * 由壁纸取色会破坏这个距离，也会让不同设备上的课表观感漂移。
 */
@Composable
fun KebiaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KebiaoColorScheme.Dark else KebiaoColorScheme.Light
    val coursePalette = if (darkTheme) CoursePalette.Dark else CoursePalette.Light

    CompositionLocalProvider(LocalCoursePalette provides coursePalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KebiaoTypography.Current,
            shapes = KebiaoShapes.Current,
            content = content
        )
    }
}
