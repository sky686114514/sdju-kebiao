package com.kebiao.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/* =========================================================================
 * 课刻 · 无障碍与降级（来源 docs/design-tokens.kt §9 + docs/UIUX.md §5.11）
 *
 * reduced motion（系统关闭动画，ANIMATOR_DURATION_SCALE == 0）：
 *  - 页面切换直接到终态；学期切换跳过 stagger 与退场
 *  - 列表项一次性全部显示；进行中脉冲完全关闭；空状态直接显示
 *  - Pager 拖动保留（直接操作不算自动动画），吸附改为瞬时
 *  - 颜色过渡保留 <= 100ms 或直接跳变
 *
 * 低内存设备：关闭脉冲与 stagger，所有 spring 退化为 tween(200, decelerate)，
 * 骨架屏关闭持续扫光改为静态占位块。
 * ========================================================================= */

/** 运行期动画能力。由 KebiaoTheme 注入，UI 层据此降级。 */
data class KebiaoMotionEnvironment(
    val reducedMotion: Boolean = false,
    val lowRam: Boolean = false
) {
    /** 是否允许播放入场 stagger。reduced motion 或低内存均关闭。 */
    val allowStagger: Boolean get() = !reducedMotion && !lowRam

    /** 是否允许运行进行中脉冲。reduced motion 或低内存均关闭（持续动画是前庭敏感用户主要不适来源）。 */
    val allowPulse: Boolean get() = !reducedMotion && !lowRam

    /** 骨架屏是否允许扫光。低内存改为静态占位块。 */
    val allowShimmer: Boolean get() = !lowRam
}

val LocalMotionEnvironment = staticCompositionLocalOf { KebiaoMotionEnvironment() }

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
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice == true
    }

    /** 一次性读取两项，供 Theme 注入。 */
    fun resolve(context: Context): KebiaoMotionEnvironment = KebiaoMotionEnvironment(
        reducedMotion = isReducedMotion(context),
        lowRam = isLowRamDevice(context)
    )
}
