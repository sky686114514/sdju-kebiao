package com.kebiao.app.ui.theme

import androidx.compose.ui.unit.IntOffset

/* =========================================================================
 * 今天 <-> 明天 内容切换的横向位移（纯函数，可单测）
 *
 * 为什么抽成函数：方向一旦写进 composable，就只剩肉眼验收 —— 「今天→明天该从右进还
 * 是从左进」这种事最容易被改反，而且改反之后不会报错，只会让用户觉得别扭。落成纯
 * 函数后由 DaySwitchMotionTest 钉死符号。
 *
 * 约定：时间是向右流逝的 —— 看向未来（明天）等价于「翻到下一页」，所以新内容从右侧进、旧内容向左退；回到今天则整体取反。
 *
 * 位移量刻意比层级推入（1/3）小：今天与明天是同一份列表换了数据，不是换页，
 * 位移过大会读成「跳到了另一个页」。
 * ========================================================================= */

object DaySwitchMotion {

    /** 位移占容器宽度的比例分母。 */
    private const val SLIDE_DIVISOR = 4

    /**
     * 新内容进入的起始像素偏移。
     * @param goingForward true = 今天 -> 明天（从右进）；false = 明天 -> 今天（从左进）。
     * @param containerWidth AnimatedContent 给到的容器宽度（px）。
     */
    fun enterOffset(goingForward: Boolean, containerWidth: Int): IntOffset =
        IntOffset(signedDistance(goingForward, containerWidth), 0)

    /**
     * 旧内容退出的目标像素偏移，与 [enterOffset] 恒好反号 —— 同一时刻两个方向必须相反，
     * 否则会出现「两个页面往同一边挤」的错位感。
     */
    fun exitOffset(goingForward: Boolean, containerWidth: Int): IntOffset =
        IntOffset(-signedDistance(goingForward, containerWidth), 0)

    private fun signedDistance(goingForward: Boolean, containerWidth: Int): Int {
        val distance = containerWidth / SLIDE_DIVISOR
        return if (goingForward) distance else -distance
    }
}
