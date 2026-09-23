package com.kebiao.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* 课刻 · 间距 Token（来源 docs/design-tokens.kt §5 + docs/UIUX.md §3.3）
 * 严格 4dp 网格。可用值只有 0/4/8/12/16/20/24/32/40/48/64。
 * 禁用 5 / 7 / 13 / 15 / 22 / 30。 */
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

    /** 课程色圆点直径。课程配色铁律②：8dp 圆点 + 约 10% 面积低饱和 tint 底。 */
    val CourseDot: Dp = 8.dp

    /** 课程卡固定高度。UIUX §5.8：骨架卡与真实卡同高，防 CLS。 */
    val CourseCardHeight: Dp = 112.dp

    /** 周视图左侧固定时间轴宽度。不随 Pager 滑动移动。UIUX §7.3。 */
    val WeekAxisWidth: Dp = 64.dp

    /** Material 3 最小触控目标。 */
    val TouchTarget: Dp = 48.dp
}

/* 课刻 · 层级 Token（Hairline First）。来源 docs/design-tokens.kt §6 */
object KebiaoElevation {
    val Flat: Dp = 0.dp

    /** 默认卡片：1dp 边框，无阴影。 */
    val Ring: Dp = 1.dp

    /** 悬浮元素：模糊 <= 8dp，且不加边框（避免幽灵卡片）。 */
    val Raised: Dp = 2.dp

    /** 弹窗、BottomSheet：只用阴影不加边框。 */
    val Overlay: Dp = 8.dp
}
