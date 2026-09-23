package com.kebiao.app.ui.theme

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 模糊半径 token（dp）。全项目唯一定义点，UI 层不许再写裸数字。 */
object BlurTokens {
    /** 页面停稳时的半径：必须恒为 0，否则常态白白挂着一层 RenderEffect 合成。 */
    val Settled: Dp = 0.dp

    /** 进出途中的峰值半径。够读成景深就行，再大就开始像"毛玻璃玩具"。 */
    val PageExit: Dp = 10.dp
}

/* =========================================================================
 * 页面退出时的高斯模糊（任务 B 的①）
 *
 * 可行性依据（已用本机 Gradle 缓存里的 aar 逐条验证，不是猜的）：
 *  - NavHost（navigation-compose 2.10.1，NavHostKt 字节码里有一条 AnimatedContent 调用）
 *    内部就是 AnimatedContent；
 *  - `composable<T> {}` 的 content lambda 接收者是 AnimatedContentScope
 *    （javap NavGraphBuilderKt 签名的第 5 个参数是 Function4<AnimatedContentScope, ...>）；
 *  - AnimatedContentScope 继承 AnimatedVisibilityScope，而后者唯一成员是
 *    `getTransition(): Transition<EnterExitState>` —— **不是** Transition<NavBackStackEntry>；
 *  - AnimatedContent 内部对每个子 key 单独跑一次 AnimatedEnterExitImpl，
 *    所以进入页走 PreEnter->Visible、退出页走 Visible->PostExit，各自持有独立的
 *    EnterExitState 实例。animateDp 的 targetValueByState 因此可以区分「谁在退出」。
 *
 * 所以：不需要 NavDisplay / Scene（那套 API 也不在这条依赖链上 —— androidx.navigation
 * 2.10.1 的全部 classes.jar 里 strings 搜不到 NavDisplay），用官方 AnimatedContentScope
 * 就够了。
 *
 * 降级： Modifier.blur 基于 RenderEffect.createBlurEffect，API 31 以下静默 no-op
 * （不崩、不模糊）。低版本 / lowRam / reduced motion 一律返回 Modifier，功能退化为
 * 纯滑动 + 缩放的景深近似（MotionTokens.Recipe 里那份），不会出现「文字糊在花花绿绿
 * 背景上」—— 因为压根没模糊，背景还是原来的实色。
 * ========================================================================= */

object BlurSupport {

    /**
     * 全平台唯一的模糊可用性判定点。UI 层禁止再写 Build.VERSION.SDK_INT 比较。
     *
     * API 31 以下 `Modifier.blur` 是 no-op：不崩溃，也不产生任何效果 —— 因此这里返回
     * false 只是省掉"每帧多搭一层 graphicsLayer"的开销，不修正确性问题。
     */
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 给 [AnimatedContentScope] 内的页面根布局加「景深模糊」：
 * 处于退出/进入途中的一页模糊，停稳的一页完全清晰。
 *
 * 用法：`composable<X> { Box(Modifier.pageExitBlur()) { XScreen(...) } }`
 */
@Composable
fun AnimatedContentScope.pageExitBlur(): Modifier {
    val env = LocalMotionEnvironment.current
    if (!BlurSupport.available || env.reducedMotion || env.lowRam) return Modifier

    val radius by transition.animateDp(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                // PreEnter -> Visible：正在进入的一页由模糊收敛到清晰。
                tween(MotionTokens.Duration.Medium, easing = MotionTokens.Ease.Decelerate)
            } else {
                // Visible -> PostExit：正在退出的一页一路模糊掉，比进场短，不拖沓。
                tween(MotionTokens.Duration.Fast, easing = MotionTokens.Ease.Accelerate)
            }
        },
        label = "pageExitBlur",
        targetValueByState = { state ->
            if (state == EnterExitState.Visible) BlurTokens.Settled else BlurTokens.PageExit
        }
    )
    // 半径为 0 时不要挂 BlurModifier：那会白白多出一层 graphicsLayer 去合成全家，
    // 而结果和没加一样。
    return if (radius.value > 0.05f) Modifier.blur(radius) else Modifier
}
