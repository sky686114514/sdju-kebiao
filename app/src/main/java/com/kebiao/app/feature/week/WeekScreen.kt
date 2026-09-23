package com.kebiao.app.feature.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.components.EmptyState
import com.kebiao.app.ui.components.ErrorState
import com.kebiao.app.ui.components.KebiaoGlassTopAppBar
import com.kebiao.app.ui.components.formatDateRange
import com.kebiao.app.ui.components.shortSemesterName
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.LocalSemanticColors
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 页面二：周视图（完整课表）
 *
 * 手机竖屏不硬塞 7 列 —— 见 WeekGrid 的说明。
 * 时间轴 64dp 固定不随滑动；线上 / 不排座课程进独立分区不进网格（AC-51），
 * 该分区见同包的 [OnlineAndUnplacedSection]。
 *
 * 动画（UIUX §5.7）：
 *  - 回到本周按钮：原地 scaleIn(0.8) + fadeIn，退出 scaleOut + fadeOut，不用弹跳不用位移
 *  - 周次切换：只换数据，网格本身 fadeIn(160) 掩盖重算白闪；网格内不做 stagger
 * ========================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    state: WeekUiState,
    onStepWeek: (delta: Int) -> Unit,
    onGoToCurrentWeek: () -> Unit,
    onOpenImport: () -> Unit,
    onCourseClick: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val env = LocalMotionEnvironment.current
    val content = state as? WeekUiState.Content

    Column(modifier = modifier.padding(contentPadding)) {
        KebiaoGlassTopAppBar(
            title = {
                Column {
                    Text(
                        text = "本周课表",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = content?.let { formatDateRange(it.startDate, it.endDate) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        WeekSwitcher(
            weekIndex = content?.weekIndex,
            totalWeeks = content?.totalWeeks,
            onStepWeek = onStepWeek,
            showBackToThisWeek = content != null && !content.isCurrentWeek,
            onGoToCurrentWeek = onGoToCurrentWeek,
            reduceMotion = env.reducedMotion
        )

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val bothContent = initialState is WeekUiState.Content && targetState is WeekUiState.Content
                if (bothContent) {
                    // 周次切换：容器刻意不动，由网格自身的 fadeIn(160)（配方 ⑩ weekGridIn）承担。
                    // 若这里也做 fade，就会与网格淡入叠加成「双重动画」，反而出现闪一下的鬼畜感。
                    (EnterTransition.None togetherWith ExitTransition.None).using(null)
                } else {
                    (fadeIn(tween(200, easing = MotionTokens.Ease.Decelerate)) togetherWith
                        fadeOut(tween(160, easing = MotionTokens.Ease.Accelerate))).using(null)
                }
            },
            label = "weekUiState"
        ) { current ->
            when (current) {
                WeekUiState.Loading -> WeekGridSkeleton()

                is WeekUiState.Empty -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    EmptyState(
                        icon = KebiaoIcons.Inbox,
                        title = "这个学期还没有课表",
                        subtitle = current.semesterName?.let { "${shortSemesterName(it)} 尚未导入任何课程" }
                            ?: "先从教务系统导入一次课表",
                        actionLabel = "去导入",
                        onAction = onOpenImport
                    )
                }

                is WeekUiState.Error -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    ErrorState(error = current.error, retryable = current.retryable, onRetry = null)
                }

                is WeekUiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    WeekGridFadeIn(
                        weekIndex = current.weekIndex,
                        reduceMotion = env.reducedMotion
                    ) {
                        WeekGrid(
                            content = current,
                            onCourseClick = onCourseClick,
                            modifier = Modifier.padding(top = KebiaoSpacing.X1)
                        )
                    }
                    if (current.onlineOrUnplaced.isNotEmpty()) {
                        OnlineAndUnplacedSection(
                            items = current.onlineOrUnplaced,
                            onCourseClick = onCourseClick
                        )
                    }
                    Spacer(Modifier.height(KebiaoSpacing.X16))
                }
            }
        }
    }
}

/**
 * 周次切换时让网格淡入一次（160ms，配方 ⑩ weekGridIn），掩盖整周重算造成的白闪。
 *
 * 用 [MutableTransitionState] 而不是 `AnimatedVisibility(visible = true)`：
 * 后者在首次加入组合树时不播放 enter，配方会静默失效。以 [weekIndex] 作 key，
 * 使得每次切换周次都会重新播一次淡入（这正是「掩盖重算白闪」的着力点）。
 */
@Composable
private fun WeekGridFadeIn(
    weekIndex: Int,
    reduceMotion: Boolean,
    content: @Composable () -> Unit
) {
    val visibleState = remember(weekIndex) { MutableTransitionState(false) }
    LaunchedEffect(weekIndex) { visibleState.targetState = true }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = if (reduceMotion) EnterTransition.None else MotionTokens.Recipe.weekGridIn(),
        exit = ExitTransition.None,
        label = "weekGridIn"
    ) {
        content()
    }
}

/** 网格骨架：静态占位块，不做 shimmer 扫光（周视图数据重算频繁，扫光会与重算抢帧）。 */
@Composable
private fun WeekGridSkeleton() {
    val base = LocalSemanticColors.current.skeletonBase
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter)
    ) {
        Spacer(Modifier.height(WEEK_HEADER_HEIGHT))
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().height(WEEK_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)
            ) {
                Box(
                    Modifier
                        .width(KebiaoSpacing.WeekAxisWidth - KebiaoSpacing.X3)
                        .height(12.dp)
                        .clip(KebiaoShapes.Current.extraSmall)
                        .background(base)
                )
                repeat(3) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(KebiaoShapes.Current.medium)
                            .background(base)
                    )
                }
            }
        }
    }
}
