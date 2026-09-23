package com.kebiao.app.feature.semester

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.components.EmptyState
import com.kebiao.app.ui.components.ErrorState
import com.kebiao.app.ui.components.KebiaoGlassTopAppBar
import com.kebiao.app.ui.components.LoadingSkeleton
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 页面三：多学期管理（UIUX §7.4）
 *
 * 这张列表是「选择器」，不需要卡片盒子 —— 用 1dp border 的横向分隔线。
 * 当前学期行不加边框、只加 8dp accent 圆点与尾随 check 图标。
 *
 * 这里的动画预算全部留给「返回今日课程页后的 500ms 学期切换编排」，
 * 本页自身只做状态间的淡入淡出，不做花哨转场。
 * ========================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(
    state: SemesterUiState,
    onBack: () -> Unit,
    onSwitch: (Long) -> Unit,
    onImportNew: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val env = LocalMotionEnvironment.current

    Column(modifier = modifier.padding(contentPadding)) {
        KebiaoGlassTopAppBar(
            title = {
                Text(
                    text = "学期管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    KebiaoIcon(
                        res = KebiaoIcons.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                if (env.reducedMotion) {
                    (androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None).using(null)
                } else {
                    (fadeIn(tween(200, easing = MotionTokens.Ease.Decelerate)) togetherWith
                        fadeOut(tween(150, easing = MotionTokens.Ease.Accelerate))).using(null)
                }
            },
            label = "semesterUiState"
        ) { current ->
            when (current) {
                SemesterUiState.Loading -> LoadingSkeleton(count = 4)

                SemesterUiState.Empty -> EmptyState(
                    icon = KebiaoIcons.Inbox,
                    title = "还没有导入课表",
                    subtitle = "用学号登录教务系统，把你的课表拉过来",
                    actionLabel = "从教务系统导入",
                    onAction = onImportNew
                )

                is SemesterUiState.Error -> ErrorState(
                    error = current.error,
                    retryable = current.retryable,
                    onRetry = null
                )

                is SemesterUiState.Content -> Column(Modifier.fillMaxSize()) {
                    TextButton(
                        onClick = onImportNew,
                        modifier = Modifier.padding(horizontal = KebiaoSpacing.X2)
                    ) {
                        KebiaoIcon(
                            res = KebiaoIcons.Add,
                            contentDescription = null,
                            size = IconSize.Inline,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "从教务系统导入新学期",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = KebiaoSpacing.X2)
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items = current.rows, key = { it.id }) { row ->
                            SemesterRowItem(
                                row = row,
                                switchable = current.switchable,
                                isSwitching = current.switchingTo == row.id,
                                onClick = { if (!row.isCurrent) onSwitch(row.id) }
                            )
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemesterRowItem(
    row: SemesterRow,
    switchable: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = switchable && !row.isCurrent, onClick = onClick)
            .padding(horizontal = KebiaoSpacing.ScreenGutter, vertical = KebiaoSpacing.X3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 当前学期用实心 accent 圆点，非当前用空心圆点（Shape 与填充双重编码）
        if (row.isCurrent) {
            Box(
                Modifier
                    .size(KebiaoSpacing.CourseDot)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Box(
                Modifier
                    .size(KebiaoSpacing.CourseDot)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }

        Spacer(Modifier.width(KebiaoSpacing.X3))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${row.startDate} 起 · ${row.totalWeeks} 教学周",
                style = AppType.meta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            isSwitching -> CircularProgressIndicator(
                modifier = Modifier.size(KebiaoSpacing.TouchTarget / 2),
                strokeWidth = 2.dp
            )
            row.isCurrent -> KebiaoIcon(
                res = KebiaoIcons.CheckCircle,
                contentDescription = "当前学期",
                size = IconSize.Standard,
                tint = MaterialTheme.colorScheme.primary
            )
            else -> Spacer(Modifier.width(KebiaoSpacing.X1))
        }
    }
}
