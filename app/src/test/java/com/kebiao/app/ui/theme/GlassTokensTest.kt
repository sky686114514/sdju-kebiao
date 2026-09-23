package com.kebiao.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [glassSurfaceAlpha] 单测 —— 顶栏玻璃的「低版本对比度兜底」是量化断言，不能靠肉眼。
 *
 * 只测 [glassSurfaceAlpha] 这一个纯函数：它不碰 Build.VERSION，在 JVM 上算出来的值与
 * 真机一致。凡是依赖 [BlurSupport.available]（读 Build.VERSION.SDK_INT）的东西都不在
 * 这里测 —— JVM 单测里 Build 是空壳、返回默认值，断言出来的是假的。
 */
class GlassTokensTest {

    /** 支持真模糊时底色必须**更透**（alpha 更小）；相等说明分档被写死成了同一个值。 */
    @Test
    fun `glass surface is more transparent when real blur is available`() {
        val withBlur = glassSurfaceAlpha(supportsBlur = true)
        val withoutBlur = glassSurfaceAlpha(supportsBlur = false)

        assertTrue(
            withBlur < withoutBlur,
            "支持模糊时应更透：期望 withBlur < withoutBlur，实得 $withBlur / $withoutBlur"
        )
    }

    /** 两档都必须是合法 alpha：0 会变成全透明（字直接压在课程卡上），1 会退化成实色。 */
    @Test
    fun `both alphas stay inside the open unit interval`() {
        listOf(true, false).forEach { supportsBlur ->
            val alpha = glassSurfaceAlpha(supportsBlur)
            assertTrue(
                alpha > 0f && alpha < 1f,
                "alpha 必须落在 (0,1) 开区间：supportsBlur=$supportsBlur 实得 $alpha"
            )
        }
    }

    /**
     * 不支持模糊的机型上，底色必须足够实 —— 这是「字糊在彩色课程卡上读不清」的量化兜底。
     * 0.9 意味着最坏情况也只有 10% 的背景透上来。
     */
    @Test
    fun `fallback alpha is opaque enough to keep top bar text readable`() {
        val fallback = glassSurfaceAlpha(supportsBlur = false)

        assertTrue(
            fallback >= 0.9f,
            "不支持模糊时 alpha 必须 >= 0.9f 以保住文字对比度，实得 $fallback"
        )
    }

    /** 更透的那一档也不能透到失去「这是一条栏」的读感。 */
    @Test
    fun `blurred alpha still reads as a surface`() {
        val withBlur = glassSurfaceAlpha(supportsBlur = true)

        assertTrue(
            withBlur >= 0.6f,
            "支持模糊时也不能透到看不出顶栏：alpha 必须 >= 0.6f，实得 $withBlur"
        )
    }
}
