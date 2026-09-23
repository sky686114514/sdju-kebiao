package com.kebiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 状态 pill（卡片右上角）
 *
 * 状态不只靠颜色：进行中同时有边框色、状态 pill、脉冲圆环三重编码（UIUX §5.11）。
 * pill 的出现用 MotionTokens.Recipe.statusPillEnter()（横向展开，不抢视线）。
 * ========================================================================= */

data class StatusPillSpec(
    val text: String,
    val container: Color,
    val content: Color,
    /** 前置小圆点颜色（null 表示不画点）。 */
    val dot: Color? = null
)

/** 由卡片状态 + 底色体系推导 pill 外观。纯函数便于单测与预览。 */
@Composable
fun statusPillSpecFor(
    state: CourseCardState,
    minutesUntilStart: Int? = null
): StatusPillSpec? {
    val cs = MaterialTheme.colorScheme
    return when (state) {
        CourseCardState.ONGOING -> StatusPillSpec(
            text = "进行中",
            container = cs.primary,
            content = cs.onPrimary,
            dot = cs.onPrimary
        )
        CourseCardState.FINISHED -> StatusPillSpec(
            text = "已结束",
            container = cs.surfaceContainerHigh,
            content = cs.onSurfaceVariant
        )
        CourseCardState.UPCOMING_SOON -> StatusPillSpec(
            text = minutesUntilStart?.let { "$it 分钟后" } ?: "即将开始",
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            dot = cs.primary
        )
        CourseCardState.ONLINE -> StatusPillSpec(
            text = ONLINE_LABEL,
            container = cs.tertiaryContainer,
            content = cs.onTertiaryContainer,
            dot = cs.tertiary
        )
        CourseCardState.PENDING -> StatusPillSpec(
            text = "待定",
            container = cs.surfaceContainerHigh,
            content = cs.onSurfaceVariant
        )
        CourseCardState.NOT_THIS_WEEK -> StatusPillSpec(
            text = "本周不上",
            container = cs.surfaceContainerHigh,
            content = cs.onSurfaceVariant
        )
        CourseCardState.LATER -> null
    }
}

@Composable
fun StatusPill(
    spec: StatusPillSpec,
    modifier: Modifier = Modifier
) {
    // pill 底色随状态平滑过渡（300ms standard），不闪跳
    val container by animateColorAsState(
        targetValue = spec.container,
        animationSpec = MotionTokens.Recipe.stateChange(),
        label = "pillContainer"
    )
    val content by animateColorAsState(
        targetValue = spec.content,
        animationSpec = MotionTokens.Recipe.stateChange(),
        label = "pillContent"
    )

    Row(
        modifier = modifier
            .clip(KebiaoShapes.Pill)
            .background(container)
            .padding(horizontal = KebiaoSpacing.X2, vertical = KebiaoSpacing.X1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)
    ) {
        if (spec.dot != null) {
            Box(
                Modifier
                    .size(KebiaoSpacing.X1)
                    .clip(CircleShape)
                    .background(content)
            )
        }
        Text(
            text = spec.text,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
