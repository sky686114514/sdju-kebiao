package com.kebiao.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEvent

/* =========================================================================
 * 侧滑返回（predictive back）的进出场 —— 用来**替换**库自带的「缩向中心 + 淡出」。
 *
 * 为什么要有这个文件：
 *  navigation-compose 2.10.1 在 manifest 开启 enableOnBackInvokedCallback 后会走
 *  DefaultNavTransitions 里那套预测式默认动画，默认值实测是
 *    predictivePopEnterTransition = fadeIn(spec = spring(1600f), initialAlpha = 0f)
 *    predictivePopExitTransition  = scaleOut(targetScale = 0.7f)
 *  —— 退出页缩到 0.7 并朝中心收缩、进入页纯淡入，正是用户描述的
 *  「界面回缩小回中心然后逐渐消失」。
 *  好消息是 NavHost 提供了同名的公开参数，可以把这两条整套换掉，
 *  见 NavHost(... predictivePopEnterTransition = ..., predictivePopExitTransition = ...)。
 *
 * 是否跟手（finger-following）：是，而且是被库强保证的。NavHostKt 内部会
 *  `rememberNavHostEventHandler(...)` 读 `getProgress()` / `getSwipeEdge()`，
 *  把 progress 喂给 `SeekableTransitionState.seekTo(...)`（javap 证据：内部类
 *  NavHostKt$NavHost$20$1 的唯一有效调用就是 SeekableTransitionState.seekTo）。
 *  所以我们给出去的这两条 transition 不是"松手后播一段"，而是被手势进度实时定位的。
 *
 * ============ 注意：不要为了「统一观感」把位移改成 1/3 ============
 *  本文件的位移是**全屏宽**，而系统返回键那条路（MotionTokens.Recipe.popExit）是
 *  1/3 屏宽。两个数字不一致是**刻意的**，不是疏漏：
 *   - popExit：松手/点按之后按时长播一段动画，1/3 足够表达"退走了"；
 *   - 本文件：转场被 `seekTo(progress)` 跟手驱动，progress∈[0,1] 直接映射到整条
 *     transition 的同一位置，因此**终态必须是彻底滑出画面**。
 *  若把这里改成 1/3，手指划到底时画面只挪 1/3，手感会立刻读作"脱手 / 发黏"，
 *  跟手就没了。改之前请先看 git blame 走到这里。
 * =========================================================================
 *
 * 为什么不加任何 fade/scale：用户明确否定了「逐渐消失」。纯平移时两页都是不透明的
 *  相邻书页，没有两层半透明相乘的发糊区间。
 * ========================================================================= */

object PredictivePopTransitions {

    /** 松手后接管剩余行程用的补间；跟手期间它在 seek，不参与计算。 */
    private val Slide: FiniteAnimationSpec<IntOffset> =
        tween(MotionTokens.Duration.Slow, easing = MotionTokens.Ease.Emphasized)

    /**
     * 当前页的滑出方向系数。
     * - 从右边缘起手：手指向左推行，画面跟手向左走 -> -1
     * - 从左边缘起手 / EDGE_NONE / 任何未知值：兜底向右走 -> +1（保持既有观感，不许崩）
     * 方向取值一律用 [NavigationEvent] 的常量，禁止写 `== 1` 这类数字。
     */
    private fun exitSign(swipeEdge: Int): Int =
        if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1

    /** 被换回来的那一页：从画面之外沿相反方向滑进原位。 */
    fun enter(swipeEdge: Int, reducedMotion: Boolean = false): EnterTransition =
        if (reducedMotion) EnterTransition.None
        else slideInHorizontally(Slide) { -exitSign(swipeEdge) * it }

    /** 当前这一页：跟着手指整体滑出画面。 */
    fun exit(swipeEdge: Int, reducedMotion: Boolean = false): ExitTransition =
        if (reducedMotion) ExitTransition.None
        else slideOutHorizontally(Slide) { exitSign(swipeEdge) * it }
}
