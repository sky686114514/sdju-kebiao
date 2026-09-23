package com.kebiao.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DaySwitchMotion] 单测 —— 今天↔明天的内容滑动方向。
 *
 * 专门针对"沉默逻辑错误"：方向写反不会报错、也不会编译失败，只会让用户在每次切换时
 * 觉得别扭。这里的断言全部钉在具体数值（含符号）上，不写「绝对值大于 0」这种等于没测
 * 的断言。
 *
 * 只依赖 JVM 上的纯 Kotlin（IntOffset 是 compose-ui-unit 的值类，不碰 Android 运行时），
 * 与 ScheduleCalculatorTest 同样只用 kotlin.test，不引入新框架。
 */
class DaySwitchMotionTest {

    private val width = 1080
    private val expected = width / 4

    @Test
    fun `going forward enters new content from the right`() {
        val offset = DaySwitchMotion.enterOffset(goingForward = true, containerWidth = width)
        assertEquals(expected, offset.x, "今天->明天，新内容应从右侧进入")
        assertTrue(offset.x > 0, "前向切换的进入位移必须为正")
        assertEquals(0, offset.y, "不涉及竖向位移")
    }

    @Test
    fun `going forward leaves old content to the left`() {
        val offset = DaySwitchMotion.exitOffset(goingForward = true, containerWidth = width)
        assertEquals(-expected, offset.x, "今天->明天，旧内容应向左退出")
        assertTrue(offset.x < 0, "前向切换的退出位移必须为负")
        assertEquals(0, offset.y, "不涉及竖向位移")
    }

    @Test
    fun `going backward inverts every sign`() {
        val enter = DaySwitchMotion.enterOffset(goingForward = false, containerWidth = width)
        val exit = DaySwitchMotion.exitOffset(goingForward = false, containerWidth = width)
        assertEquals(-expected, enter.x, "明天->今天，新内容应从左侧恢复")
        assertTrue(enter.x < 0, "反向切换的进入位移必须为负")
        assertEquals(expected, exit.x, "明天->今天，旧内容应向右退走")
        assertTrue(exit.x > 0, "反向切换的退出位移必须为正")
    }

    @Test
    fun `enter and exit always move in opposite directions`() {
        listOf(true, false).forEach { forward ->
            val enter = DaySwitchMotion.enterOffset(forward, width)
            val exit = DaySwitchMotion.exitOffset(forward, width)
            assertEquals(-enter.x, exit.x, "同一时刻进出位移必须严格反号：$forward")
            assertEquals(enter.y, exit.y)
        }
    }

    @Test
    fun `zero width produces zero travel instead of blowing up`() {
        assertEquals(0, DaySwitchMotion.enterOffset(true, 0).x)
        assertEquals(0, DaySwitchMotion.exitOffset(false, 0).x)
        assertEquals(0, DaySwitchMotion.enterOffset(true, 3).x, "宽度不足 1/4 时取整为 0，不放大")
    }
}
