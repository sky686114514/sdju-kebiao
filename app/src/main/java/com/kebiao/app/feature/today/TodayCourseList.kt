package com.kebiao.app.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import com.kebiao.app.ui.components.CourseCard
import com.kebiao.app.ui.components.CourseCardState
import com.kebiao.app.ui.components.SectionHeader
import com.kebiao.app.ui.components.courseCardStateOf
import com.kebiao.app.ui.components.minutesUntilStart
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens
import com.kebiao.app.ui.theme.LocalSemanticColors
import java.time.LocalTime

/* =========================================================================
 * 今日课程列表
 *
 * 三条硬约束（UIUX §5.4 §5.5 §5.10）：
 *  1. stagger 只在首屏静止时播一次（rememberSaveable），滚动回看不重播；
 *     间隔 60ms、封顶 6 项 -> 同时动画数 = 180/60 = 3，恰好等于上限。
 *  2. 状态提升到列表层：全屏只允许 1 门课处于「进行中」，用 derivedStateOf 保证。
 *  3. 滚动期间动画数恒为 0 —— 入场是 AnimatedVisibility 的一次性 enter，
 *     不随滚动重启。
 *
 * 无时间地点课（军事技能）单独归入「待定」分组，不塞进时间轴、不显示假时间。
 * ========================================================================= */

@Composable
fun TodayCourseList(
    state: TodayUiState.Content,
    now: LocalTime,
    onCourseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 88.dp
) {
    val listState = rememberLazyListState()

    // 全屏唯一「进行中」：即使数据异常出现两门重叠，也只高亮更早开始的那一门
    val ongoingSessionId by remember(state.items, now) {
        derivedStateOf {
            state.items
                .filter { courseCardStateOf(it, now) == CourseCardState.ONGOING }
                .minByOrNull { it.startTime ?: LocalTime.MAX }
                ?.sessionId
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val resumed = lifecycleState == Lifecycle.State.RESUMED

    val timed = state.items.filter { it.startTime != null && it.endTime != null }
    val unplaced = state.items.filter { it.startTime == null || it.endTime == null }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = KebiaoSpacing.ScreenGutter,
            end = KebiaoSpacing.ScreenGutter,
            top = KebiaoSpacing.X2,
            bottom = contentBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.CardGap)
    ) {
        if (state.allFinished) {
            item(key = "all-finished-banner") {
                AllFinishedBanner(count = timed.size)
            }
        }

        itemsIndexed(
            items = timed,
            key = { _, occurrence -> occurrence.sessionId }
        ) { index, occurrence ->
            StaggeredCard(
                index = index,
                saveKey = occurrence.sessionId,
                modifier = Modifier.animateItem(placementSpec = MotionTokens.Spring.SpatialOffset)
            ) {
                CourseCard(
                    occurrence = occurrence,
                    state = courseCardStateOf(occurrence, now),
                    minutesUntilStart = minutesUntilStart(occurrence, now),
                    showPulse = occurrence.sessionId == ongoingSessionId,
                    lifecycleResumed = resumed,
                    onClick = { onCourseClick(occurrence.sessionId) }
                )
            }
        }

        if (unplaced.isNotEmpty()) {
            item(key = "section-unplaced") {
                SectionHeader(
                    title = "待定",
                    icon = KebiaoIcons.Help,
                    modifier = Modifier.padding(top = KebiaoSpacing.X3)
                )
            }
            itemsIndexed(
                items = unplaced,
                key = { _, occurrence -> occurrence.sessionId }
            ) { index, occurrence ->
                StaggeredCard(
                    index = index,
                    saveKey = occurrence.sessionId * 31 + 7,
                    modifier = Modifier.animateItem(placementSpec = MotionTokens.Spring.SpatialOffset)
                ) {
                    CourseCard(
                        occurrence = occurrence,
                        state = CourseCardState.PENDING,
                        showPulse = false,
                        lifecycleResumed = resumed,
                        onClick = { onCourseClick(occurrence.sessionId) }
                    )
                }
            }
        }
    }
}

/**
 * 单项 stagger 包装。首屏播一次，之后用 rememberSaveable 标记不再重播。
 * reduced motion / 低内存设备下一次到位，无 stagger 无位移。
 */
@Composable
private fun StaggeredCard(
    index: Int,
    saveKey: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val env = LocalMotionEnvironment.current
    val inPreview = LocalInspectionMode.current
    var played by rememberSaveable(saveKey) { mutableStateOf(false) }

    val shouldAnimate = !inPreview && env.allowStagger && !played
    // 要点：`AnimatedVisibility(visible = true)` 在「首次加入组合树时」并不会播放 enter
    // （其内部 updateTransition 的起始态就是 visible，无可动之事），于是 stagger 静默失效。
    // 想「一进树就播」必须走 MutableTransitionState 变体 —— 这是本配方能真正生效的前提。
    val visibleState = remember(saveKey) { MutableTransitionState(!shouldAnimate) }
    LaunchedEffect(saveKey) {
        if (shouldAnimate) visibleState.targetState = true
        played = true
    }

    val enter: EnterTransition =
        if (shouldAnimate) MotionTokens.Recipe.listItemEnter(MotionTokens.Recipe.staggerDelayFor(index))
        else EnterTransition.None

    AnimatedVisibility(
        visibleState = visibleState,
        enter = enter,
        exit = ExitTransition.None,
        modifier = modifier,
        label = "courseItemEnter"
    ) {
        content()
    }
}

/** 今日已上完提示条。不替换列表——用户仍可回看今天上过什么。 */
@Composable
private fun AllFinishedBanner(count: Int) {
    val semantic = LocalSemanticColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionTokens.Recipe.quickColorChange(),
        label = "allFinishedIn"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(progress)
            .background(semantic.success.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(KebiaoSpacing.X3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KebiaoIcon(
            res = KebiaoIcons.CheckCircle,
            contentDescription = null,
            size = IconSize.Inline,
            tint = semantic.success
        )
        Spacer(Modifier.width(KebiaoSpacing.X2))
        Text(
            text = "今天的课都上完了，共 $count 门",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
