package com.kebiao.app.feature.week

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 周次切换器 + 「回到本周」（从 WeekScreen 拆出，避免单文件超过 300 行）
 *
 * 动画：回到本周按钮原地 scaleIn/fadeIn 出现，scaleOut/fadeOut 消失，
 * 不用弹跳也不用水平位移（位移会暗示层级方向，而这里只是「跳回当前周」）。
 * reduced motion 时两端都是 None，按钮直接切换。
 * ========================================================================= */

@Composable
internal fun WeekSwitcher(
    weekIndex: Int?,
    totalWeeks: Int?,
    onStepWeek: (Int) -> Unit,
    showBackToThisWeek: Boolean,
    onGoToCurrentWeek: () -> Unit,
    reduceMotion: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter, vertical = KebiaoSpacing.X1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(KebiaoShapes.Pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = KebiaoSpacing.X1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onStepWeek(-1) }) {
                KebiaoIcon(
                    res = KebiaoIcons.ChevronLeft,
                    contentDescription = "上一周",
                    size = IconSize.Compact,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = weekIndex?.let { "第 $it / ${totalWeeks ?: 0} 周" } ?: "第 -- 周",
                style = AppType.time(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            IconButton(onClick = { onStepWeek(1) }) {
                KebiaoIcon(
                    res = KebiaoIcons.ChevronRight,
                    contentDescription = "下一周",
                    size = IconSize.Compact,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = showBackToThisWeek,
            enter = if (reduceMotion) EnterTransition.None
            else MotionTokens.Recipe.backToThisWeekEnter(),
            exit = if (reduceMotion) ExitTransition.None
            else MotionTokens.Recipe.backToThisWeekExit()
        ) {
            IconButton(onClick = onGoToCurrentWeek) {
                KebiaoIcon(
                    res = KebiaoIcons.Today,
                    contentDescription = "回到本周",
                    size = IconSize.Standard,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
