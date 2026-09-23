package com.kebiao.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.LocalSemanticColors
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 骨架屏（UIUX §5.8）
 *
 * 3 张骨架卡，高度与真实课程卡一致（112dp），防 CLS。
 * shimmer：1200ms 线性 Restart，高光带 24% 宽度。
 * 低内存设备：关闭持续扫光，改为静态占位块（UIUX §5.11）。
 *
 * 高光带作为覆盖层绘制（最后一个子元素），确保盖在占位块之上。
 * ========================================================================= */

@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    count: Int = MotionTokens.Rules.SkeletonCardCount
) {
    val env = LocalMotionEnvironment.current
    val shimmer = rememberInfiniteTransition(label = "skeletonShimmer")
    val progress by shimmer.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.Duration.Shimmer, easing = MotionTokens.Ease.Linear),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    // 关闭扫光时把 progress 钉在带外，等价于静态占位块
    val effective = if (env.allowShimmer) progress else -1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.CardGap)
    ) {
        repeat(count) { SkeletonCard(progress = effective) }
    }
}

@Composable
private fun SkeletonCard(progress: Float) {
    val semantic = LocalSemanticColors.current
    val base = semantic.skeletonBase
    val highlight = semantic.skeletonHighlight
    val blockShape = RoundedCornerShape(KebiaoSpacing.X1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KebiaoSpacing.CourseCardHeight)
            .clip(KebiaoShapes.Current.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(KebiaoSpacing.CardPadding)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(KebiaoSpacing.CourseDot)
                    .clip(CircleShape)
                    .background(base)
            )
            Spacer(Modifier.width(KebiaoSpacing.X3))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(18.dp)
                        .clip(blockShape)
                        .background(base)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.44f)
                        .height(14.dp)
                        .clip(blockShape)
                        .background(base)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.52f)
                        .height(14.dp)
                        .clip(blockShape)
                        .background(base)
                )
            }
        }
        // 覆盖层：24% 宽度高光带随 progress 扫过
        Box(
            Modifier
                .matchParentSize()
                .shimmerBand(progress, base, highlight)
        )
    }
}

/** 24% 宽度高光带随 progress 从左扫到右的绘制修饰符。 */
private fun Modifier.shimmerBand(progress: Float, base: Color, highlight: Color): Modifier =
    drawBehind {
        val band = size.width * MotionTokens.Rules.ShimmerHighlightFraction
        val x = progress * (size.width + band) - band
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x, 0f),
                end = Offset(x + band, 0f)
            )
        )
    }
