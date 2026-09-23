package com.kebiao.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 课程卡装饰件 —— 从 CourseCard.kt 拆出，保持单文件 <= 300 行。
 *
 * 这里的三个成员都不改变卡片布局（不触发 relayout）：
 *   PulsingRing   只动 scale / alpha 的进行中脉冲圆环（graphicsLayer）
 *   MetaRow       图标 + 单行元信息的紧凑行
 *   dashedBorder  画在既有轮廓上的虚线边框（drawBehind，不占布局）
 * ========================================================================= */

/** 进行中的外扩脉冲圆环：scale 1.0 到 1.35，alpha 0.35 到 0.0，1600ms Reverse。 */
@Composable
internal fun PulsingRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "inProgressPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.Rules.PulsePeriodMs, easing = MotionTokens.Ease.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringProgress"
    )
    Box(
        Modifier
            .size(KebiaoSpacing.CourseDot)
            .graphicsLayer {
                scaleX = 1f + 0.35f * progress
                scaleY = 1f + 0.35f * progress
                alpha = 0.35f * (1f - progress)
            }
            .clip(CircleShape)
            .background(color)
    )
}

/** 图标 + 单行元信息。图标与文字同色，保证 16dp 下的对比度。 */
@Composable
internal fun MetaRow(
    icon: Int,
    text: String,
    iconTint: Color,
    textColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KebiaoIcon(res = icon, contentDescription = null, size = IconSize.Inline, tint = iconTint)
        Spacer(Modifier.width(KebiaoSpacing.InlineIconGap))
        Text(
            text = text,
            style = AppType.meta(),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 虚线边框。用于「本周不上」的第二重编码（不依赖颜色）。
 * 画在 rounded rect 轮廓上，虚线以 8dp 实 / 6dp 空交替。
 */
internal fun Modifier.dashedBorder(
    thickness: Dp,
    color: Color,
    shape: Shape
): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = thickness.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
            phase = 0f
        )
    )
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        color = color,
        style = stroke
    )
}
