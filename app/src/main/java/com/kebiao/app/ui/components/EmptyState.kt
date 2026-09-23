package com.kebiao.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 空状态（UIUX §5.6）
 *
 * 入场编排：图标 scaleIn(0.88) + fadeIn 260ms emphasized；文案 slideIn + fadeIn
 * 260ms decelerate 延时 160ms。总 420ms。
 *
 * 禁止写「暂无数据」「还没有课程」——这两种文案都没有给用户任何信息。
 * 四种空状态文案必须可区分：今天没有课程 / 假期中 / 学期未开始 / 尚未导入。
 * ========================================================================= */

@Composable
fun EmptyState(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** 「下节课」那种带真实数据的行动提示，比 subtitle 更强调。 */
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val env = LocalMotionEnvironment.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val iconEnter = if (env.reducedMotion) EnterTransition.None else MotionTokens.Recipe.emptyStateIconEnter()
    val textEnter = if (env.reducedMotion) EnterTransition.None else MotionTokens.Recipe.emptyStateTextEnter()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter, vertical = KebiaoSpacing.X10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = visible, enter = iconEnter) {
            KebiaoIcon(
                res = icon,
                contentDescription = null,
                size = IconSize.Standard,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(KebiaoSpacing.X4))
        AnimatedVisibility(visible = visible, enter = textEnter) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                if (hint != null) {
                    Text(
                        text = hint,
                        style = AppType.meta(),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(KebiaoSpacing.X1))
                    TextButton(onClick = onAction) {
                        KebiaoIcon(
                            res = KebiaoIcons.CalendarViewWeek,
                            contentDescription = null,
                            size = IconSize.Inline,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = KebiaoSpacing.X2)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「今天 / 明天没有课程」专用：event_busy 图标 + 下节课真实提示 + 查看本周课表。
 *
 * [title] 带默认值：切到明天时传「明天没有课程」，其余调用点不受影响。
 */
@Composable
fun NoClassTodayState(
    nextClassLine: String?,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "今天没有课程"
) {
    EmptyState(
        icon = KebiaoIcons.EventBusy,
        title = title,
        hint = nextClassLine,
        actionLabel = "查看本周课表",
        onAction = onOpenWeek,
        modifier = modifier
    )
}

/** 尚未导入课表：inbox 图标 + 明确下一步动作「去导入」。 */
@Composable
fun NotImportedState(
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = KebiaoIcons.Inbox,
        title = "还没有导入课表",
        subtitle = "用学号登录教务系统，把你的课表拉过来",
        actionLabel = "去导入",
        onAction = onImport,
        modifier = modifier
    )
}

/** 学期之外：区分「学期未开始」与「假期中」，两者文案不同。 */
@Composable
fun OutsideSemesterState(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    EmptyState(
        icon = KebiaoIcons.DateRange,
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier
    )
}
