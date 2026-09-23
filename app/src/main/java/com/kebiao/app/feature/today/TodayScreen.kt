package com.kebiao.app.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kebiao.app.ui.components.ErrorState
import com.kebiao.app.ui.components.KebiaoGlassTopAppBar
import com.kebiao.app.ui.components.LoadingSkeleton
import com.kebiao.app.ui.components.NoClassTodayState
import com.kebiao.app.ui.components.NotImportedState
import com.kebiao.app.ui.components.OutsideSemesterState
import com.kebiao.app.ui.components.formatNextClassLine
import com.kebiao.app.ui.components.formatTodaySubtitle
import com.kebiao.app.ui.components.formatWeekLabel
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 页面一：今日课程（主界面，核心）
 *
 * 首屏第一眼必须是今天真实的课程数据，不是标题也不是宣传语。
 *
 * 过渡编排：
 *  - UiState 分支之间：fade-through（Loading <-> Content 用 200ms crossfade，
 *    避免骨架屏切换时的白闪）。
 *  - Content 内部按 semesterId 再做一层 AnimatedContent，承载 UIUX §5.3 的
 *    500ms 学期切换编排（旧列表 140ms 退场 -> 容器 spring(400) 变高 -> 新列表 stagger）。
 * ========================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    isRefreshing: Boolean,
    viewingTomorrow: Boolean,
    onRefresh: () -> Unit,
    onToggleTomorrow: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSemesters: () -> Unit,
    onCourseClick: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val env = LocalMotionEnvironment.current
    val week = when (state) {
        is TodayUiState.Content -> state.week
        is TodayUiState.NoClassToday -> state.week
        else -> null
    }
    val date = when (state) {
        is TodayUiState.Content -> state.date
        is TodayUiState.NoClassToday -> state.date
        else -> null
    }
    val semesterName = when (state) {
        is TodayUiState.Content -> state.semesterName
        is TodayUiState.NoClassToday -> state.semesterName
        is TodayUiState.OutsideSemester -> state.semesterName
        else -> null
    }
    // 标题与空状态文案都随「今天 / 明天」切换，其余分支（未导入 / 学期外）与日期无关。
    val dayLabel = if (viewingTomorrow) "明天" else "今天"

    Column(modifier = modifier.padding(contentPadding)) {
        KebiaoGlassTopAppBar(
            title = {
                Column {
                    Text(
                        text = week?.let { "$dayLabel · ${formatWeekLabel(it)}" } ?: dayLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (date != null) {
                        Text(
                            text = formatTodaySubtitle(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                SemesterChip(semesterName = semesterName, onClick = onOpenSemesters)
                IconButton(onClick = onToggleTomorrow) {
                    KebiaoIcon(
                        res = if (viewingTomorrow) KebiaoIcons.Today else KebiaoIcons.ChevronRight,
                        contentDescription = if (viewingTomorrow) "回到今天" else "查看明天课程",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    KebiaoIcon(
                        res = KebiaoIcons.Refresh,
                        contentDescription = "更新课表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    when {
                        // 同一个学期内的 Content/NoClassToday 互相变化（每分钟的 now tick、
                        // 今天↔明天、下拉刷新）都不该整页重播。"今天↔明天"的切换由下面
                        // 那层按 (semesterId, viewingTomorrow) 为 key 的 AnimatedContent
                        // 负责；两层同时动会出现双轴错位。
                        isSameDayContent(initialState, targetState) ->
                            EnterTransition.None togetherWith ExitTransition.None

                        initialState is TodayUiState.Loading || targetState is TodayUiState.Loading ->
                            // 骨架屏 <-> 真实内容：200ms Decelerate 交叉淡入（配方 ⑫）
                            fadeIn(MotionTokens.Recipe.skeletonCrossfade()) togetherWith
                                fadeOut(tween(200, easing = MotionTokens.Ease.Accelerate))

                        else ->
                            MotionTokens.Recipe.fadeThroughEnter(env.reducedMotion) togetherWith
                                MotionTokens.Recipe.fadeThroughExit(env.reducedMotion)
                    }.using(null)
                },
                label = "todayUiState"
            ) { current ->
                when (current) {
                    TodayUiState.Loading -> LoadingSkeleton()

                    TodayUiState.NotImported -> ScrollableState { NotImportedState(onImport = onOpenImport) }

                    is TodayUiState.OutsideSemester -> ScrollableState {
                        OutsideSemesterState(
                            title = if (current.notStartedYet) "学期还没开始" else "正在假期中",
                            subtitle = if (current.notStartedYet) {
                                val start = current.startDate?.let { "，${it} 开学" } ?: ""
                                "${current.semesterName ?: "当前学期"}还没开始$start"
                            } else {
                                "${current.semesterName ?: "本学期"}已经结束，安心放假"
                            }
                        )
                    }

                    is TodayUiState.NoClassToday -> ScrollableState {
                        NoClassTodayState(
                            title = if (viewingTomorrow) "明天没有课程" else "今天没有课程",
                            nextClassLine = current.nextClass?.let { "下节课：${formatNextClassLine(it)}" },
                            onOpenWeek = onOpenWeek
                        )
                    }

                    is TodayUiState.Content -> SemesterKeyedCourseList(
                        state = current,
                        viewingTomorrow = viewingTomorrow,
                        onCourseClick = onCourseClick,
                        reduceMotion = env.reducedMotion
                    )

                    is TodayUiState.Error -> ScrollableState {
                        ErrorState(
                            error = current.error,
                            retryable = current.retryable,
                            onRetry = onRefresh
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content 分支内再做一层 AnimatedContent —— 学期切换与「今天↔明天」共用这一层，
 * key 是 [TodayContentKey]：
 *  - semesterId 变 -> 竖向编排（旧列表向上退、新列表从下进，UIUX §5.3 的 500ms 预算）；
 *  - viewingTomorrow 变 -> 横向滑动，方向由 [DaySwitchMotion] 给；
 * 两者互斥：先看学期。
 */
@Composable
private fun SemesterKeyedCourseList(
    state: TodayUiState.Content,
    viewingTomorrow: Boolean,
    onCourseClick: (Long) -> Unit,
    reduceMotion: Boolean
) {
    AnimatedContent(
        targetState = TodayContentKey(state.semesterId, viewingTomorrow),
        transitionSpec = {
            when {
                reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                initialState.semesterId != targetState.semesterId ->
                    MotionTokens.Recipe.semesterListIn() togetherWith MotionTokens.Recipe.staleListExit()
                else -> {
                    val goingForward = !initialState.viewingTomorrow && targetState.viewingTomorrow
                    MotionTokens.Recipe.daySwitchEnter(goingForward) togetherWith
                        MotionTokens.Recipe.daySwitchExit(goingForward)
                }
            }.using(null)
        },
        label = "semesterAndDaySwitch"
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(animationSpec = MotionTokens.Spring.SpatialSize)
        ) {
            TodayCourseList(
                state = state,
                now = state.now,
                onCourseClick = onCourseClick
            )
        }
    }
}

/**
 * 两个状态是否为「同一学期的同一份日视图」—— 即列表本体没换，只是内容刷新了。
 * 判据是「同类分支 + 同 semesterId」，不是整个对象相等：Content 里带着每分钟变化的
 * `now`，按对象相等的话每一分钟都会重播一次整页转场。
 */
private fun isSameDayContent(initial: TodayUiState, target: TodayUiState): Boolean {
    val kind = initial.dayListKind()
    return kind != 0 && kind == target.dayListKind() &&
        initial.dayListSemesterId() == target.dayListSemesterId()
}

private fun TodayUiState.dayListKind(): Int = when (this) {
    is TodayUiState.Content -> 1
    is TodayUiState.NoClassToday -> 2
    else -> 0
}

private fun TodayUiState.dayListSemesterId(): Long? = when (this) {
    is TodayUiState.Content -> semesterId
    is TodayUiState.NoClassToday -> semesterId
    else -> null
}

/** 内容层的合成 key：学期决定数据集，是否看明天决定渲染哪一天。 */
private data class TodayContentKey(val semesterId: Long, val viewingTomorrow: Boolean)

/** 把非列表状态包进可滚动容器，让下拉刷新手势在这些状态下同样可用。 */
@Composable
private fun ScrollableState(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(KebiaoSpacing.X8))
        content()
    }
}
