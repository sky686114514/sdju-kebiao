package com.kebiao.app.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* =========================================================================
 * 课刻 · 玻璃（毛玻璃）Token
 *
 * 与 [BlurSupport] 的分工（两处都不要越界）：
 *  - [BlurSupport.available]（ExitBlur.kt）是全项目**唯一**的 SDK 判定点；
 *  - 本文件只做「给定可用性 -> 取什么值」的纯计算，因此可写 JVM 单测
 *    （GlassTokensTest.kt 断言的正是这两个函数，不碰 Build.VERSION）。
 *
 * 已知边界（写在这里是为了不再有人重复踩）：
 *  `Modifier.blur` 模糊的是**该节点自身**的绘制内容，拿不到它背后兄弟节点的像素。
 *  所以只有「把要模糊的那层内容自己加 blur」这条路可行 —— 即任务③（弹窗背后的内容层）。
 *  任务②的顶栏做不到真模糊：6 个屏的 TopAppBar 与内容都是 Column 上下排布、互不重叠，
 *  顶栏背后只有一层纯色页面背景，既没有可模糊的内容，也没有内容会滚到它下面去。
 * ========================================================================= */

private const val TOP_BAR_ALPHA_WITH_BLUR = 0.72f
private const val TOP_BAR_ALPHA_WITHOUT_BLUR = 0.94f

/** 半径小于该值时 blur 与不加毫无区别，但要多搭一层 graphicsLayer，所以直接不挂。 */
private const val MIN_EFFECTIVE_RADIUS = 0.05f

object GlassTokens {

    /**
     * 顶栏玻璃底色的不透明度（任务②）。
     * 分档理由见 [glassSurfaceAlpha]。
     */
    val TopBarAlphaWithBlur: Float = TOP_BAR_ALPHA_WITH_BLUR
    val TopBarAlphaWithoutBlur: Float = TOP_BAR_ALPHA_WITHOUT_BLUR

    /**
     * 弹窗 / 浮层打开时，其**下方内容层**的高斯模糊半径（任务③）。
     * 比页面退出的 [BlurTokens.PageExit]（10dp）更大：弹窗是模态的，需要把背景压得更远，
     * 且它一屏只有一个，成本可控。
     */
    val DialogBackdrop: Dp = 18.dp
}

/**
 * 顶栏玻璃底色的不透明度分档（纯函数，不依赖 SDK，可单测）。
 *
 * - 支持真模糊（API 31+）：更透（0.72）。内容若从栏下滚过，透出来的部分会被那一层的
 *   blur 糊掉，可读性由模糊本身兜底。
 * - 不支持模糊（API < 31，[BlurSupport.available] == false）：更实（0.94）。
 *   `Modifier.blur` 在这些版本上是静默 no-op，如果还用 0.72，花花绿绿的课程卡会直接
 *   透上来顶在标题文字后面 —— 这正是本项目点名禁止的「字糊在彩色课程卡上读不清」。
 *   0.94 下最坏情况只剩 6% 的底色透出，onSurface 对它的对比度仍远高于 4.5:1。
 *
 * 断言见 GlassTokensTest：两档必须落在 (0,1) 开区间、不支持时 >= 0.9f，
 * 且支持时严格更透（**大小关系**，不是相等 —— 相等说明分档被写死了）。
 */
fun glassSurfaceAlpha(supportsBlur: Boolean): Float =
    if (supportsBlur) TOP_BAR_ALPHA_WITH_BLUR else TOP_BAR_ALPHA_WITHOUT_BLUR

/**
 * 给「弹窗之下的那一层内容」加背景模糊：浮层打开时半径 0 -> [GlassTokens.DialogBackdrop]。
 *
 * 为什么挂在这一层就能生效：[Modifier.blur] 模糊的是它所在节点**自己的**绘制内容，
 * 而这一层正是弹窗盖住的那部分 UI，所以这就是真正的「背景模糊」，不是近似。
 *
 * 降级口径与 [pageExitBlur] 保持一致：
 *  - API < 31：`Modifier.blur` 是 no-op，直接返回 [Modifier]，省掉一层合成。
 *    Material3 的 AlertDialog 自带全屏 scrim，可读性不依赖这一层模糊。
 *  - lowRam：RenderEffect 在低端 GPU 上是纯开销，直接不挂。
 *  - reducedMotion：静态模糊本身不是位移，效果保留，但半径变化用 [snap] 直接跳变，
 *    不播任何过渡。
 */
@Composable
fun Modifier.dialogBackdropBlur(active: Boolean): Modifier {
    val env = LocalMotionEnvironment.current
    if (!BlurSupport.available || env.lowRam) return this

    val radius by animateDpAsState(
        targetValue = if (active) GlassTokens.DialogBackdrop else BlurTokens.Settled,
        animationSpec = if (env.reducedMotion) {
            snap()
        } else {
            tween(MotionTokens.Duration.Fast, easing = MotionTokens.Ease.Decelerate)
        },
        label = "dialogBackdropBlur"
    )
    return if (radius.value > MIN_EFFECTIVE_RADIUS) blur(radius) else this
}
