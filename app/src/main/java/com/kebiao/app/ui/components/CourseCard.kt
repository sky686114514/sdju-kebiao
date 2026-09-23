package com.kebiao.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.Dimming
import com.kebiao.app.ui.theme.KebiaoElevation
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalAccentTint
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens
import com.kebiao.app.ui.theme.courseColorFor

/* =========================================================================
 * 课程卡（今日课程主界面的核心组件）—— 固定高度 112dp（widthIn 兜底字号缩放）
 *
 * 结构（Spec §7.2）：
 *   左   8dp 课程色圆点（按 occurrence.accentIndex 取色，与小组件同色），垂直对齐课程名首行
 *   中   课程名 titleMd / 时间行 label + schedule 图标 / 地点行 bodySm muted + location_on
 *   右   状态 pill
 *
 * 课程配色铁律：色只出现在 8dp 圆点 + 约 10% 面积低饱和 tint 底，绝不整格铺满；
 * 课程名文字永远中性色；禁止 border-left 色条。
 *
 * 状态变「进行中」= 边框色 + 边框宽 + 底色 + pill + 脉冲圆环 5 重变化，
 * 全程无任何位置或尺寸变化 —— 用户正在读的那一行不会跑掉。
 *
 * 性能：只动 alpha / scale，全部走 graphicsLayer；同时动画元素 <= 3。
 * 装饰件（脉冲圆环 / MetaRow / 虚线边框）在 CourseCardDecoration.kt。
 * ========================================================================= */

@Composable
fun CourseCard(
    occurrence: SessionOccurrence,
    state: CourseCardState,
    modifier: Modifier = Modifier,
    /** 「即将开始」时距上课的分钟数，由列表层用统一注入的 now 算出；null 表示不适用。 */
    minutesUntilStart: Int? = null,
    /** 是否绘制并运行进行中脉冲圆环。全屏只允许 1 门为 true。 */
    showPulse: Boolean = false,
    /** 生命周期已切到后台时为 false，脉冲立即停止（不进后台无限动画耗电）。 */
    lifecycleResumed: Boolean = true,
    onClick: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val accentTint = LocalAccentTint.current.value
    val env = LocalMotionEnvironment.current

    val courseColor = courseColorFor(occurrence.accentIndex)

    val isOngoing = state == CourseCardState.ONGOING
    val isFinished = state == CourseCardState.FINISHED
    val isNotThisWeek = state == CourseCardState.NOT_THIS_WEEK
    // 已结束与「本周不上」都把内容降到 55% 不透明度（状态不只靠颜色编码）
    val isDimmed = isFinished || isNotThisWeek

    // 1) 边框色
    val borderColor by animateColorAsState(
        targetValue = if (isOngoing) cs.primary else cs.outline,
        animationSpec = MotionTokens.Recipe.stateChange(),
        label = "cardBorderColor"
    )
    // 2) 边框宽度：border 走 drawBehind，只失效绘制不触发 relayout
    val borderWidth by animateDpAsState(
        targetValue = if (isOngoing) 1.5.dp else KebiaoElevation.Ring,
        animationSpec = MotionTokens.Recipe.stateChange(),
        label = "cardBorderWidth"
    )
    // 3) 底色
    val cardBg by animateColorAsState(
        targetValue = if (isOngoing) accentTint else cs.surface,
        animationSpec = MotionTokens.Recipe.stateChange(),
        label = "cardBackground"
    )

    val cardShape = KebiaoShapes.Current.medium
    val pillSpec = statusPillSpecFor(
        state = state,
        minutesUntilStart = if (state == CourseCardState.UPCOMING_SOON) minutesUntilStart else null
    )

    val a11y = buildString {
        append(occurrence.courseName)
        formatTimeRange(occurrence.startTime, occurrence.endTime)
            .takeIf { it.isNotEmpty() }?.let { append("，$it") }
        occurrence.room?.takeIf { it.isNotBlank() }?.let { append("，$it") }
        if (occurrence.isOnline) append("，$ONLINE_LABEL")
        occurrence.teacher?.takeIf { it.isNotBlank() }?.let { append("，$it") }
        append("，").append(stateLabelOf(state))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = KebiaoSpacing.CourseCardHeight)
            .clip(cardShape)
            .background(cardBg)
            // 「本周不上」用虚线边框（多重编码：文字降透明 + 虚线 + pill）；其余走实线
            .then(
                if (isNotThisWeek) {
                    Modifier.dashedBorder(thickness = KebiaoElevation.Ring, color = cs.outline, shape = cardShape)
                } else {
                    Modifier.border(borderWidth, borderColor, cardShape)
                }
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .padding(KebiaoSpacing.CardPadding),
        verticalAlignment = Alignment.Top
    ) {
        // 左：8dp 课程色圆点（进行中时让位给脉冲圆环）
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(if (isOngoing) 16.dp else KebiaoSpacing.CourseDot),
            contentAlignment = Alignment.Center
        ) {
            if (isOngoing && showPulse && env.allowPulse && lifecycleResumed) {
                PulsingRing(color = courseColor.value)
            } else {
                Box(
                    Modifier
                        .size(KebiaoSpacing.CourseDot)
                        .clip(CircleShape)
                        .background(courseColor.value)
                )
            }
        }

        Spacer(Modifier.width(KebiaoSpacing.X3))

        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (isDimmed) Dimming.NOT_THIS_WEEK_ALPHA else 1f),
            verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)
        ) {
            Text(
                text = occurrence.courseName,
                style = AppType.courseName(),
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MetaRow(
                icon = if (state == CourseCardState.UPCOMING_SOON) KebiaoIcons.Alarm else KebiaoIcons.Schedule,
                text = formatTimeLine(occurrence),
                iconTint = if (state == CourseCardState.UPCOMING_SOON) cs.onSurface else cs.onSurfaceVariant,
                textColor = if (state == CourseCardState.UPCOMING_SOON) cs.onSurface else cs.onSurfaceVariant
            )
            when {
                state == CourseCardState.PENDING -> MetaRow(
                    icon = KebiaoIcons.Help,
                    text = TBD_LOCATION_LABEL,
                    iconTint = cs.onSurfaceVariant,
                    textColor = cs.onSurfaceVariant
                )

                occurrence.isOnline -> MetaRow(
                    icon = KebiaoIcons.Wifi,
                    text = buildString {
                        append(ONLINE_LABEL)
                        occurrence.room?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        occurrence.teacher?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    iconTint = cs.tertiary,
                    textColor = cs.onSurfaceVariant
                )

                else -> MetaRow(
                    icon = KebiaoIcons.LocationOn,
                    text = formatPlaceAndTeacher(occurrence) ?: TBD_LOCATION_LABEL,
                    iconTint = cs.onSurfaceVariant,
                    textColor = cs.onSurfaceVariant
                )
            }
        }

        // 状态 pill 只在「状态发生改变而出现」时横向展开（statusPillEnter）。
        // 刻意不用 MutableTransitionState 做首帧入场：首屏可能同时有 8 张卡，全部展开会
        // 突破「同时动画元素 <= 3」的硬约束。首帧直接可见、变更时才有动画，正是本配方要的。
        val spec = pillSpec
        AnimatedVisibility(
            visible = spec != null,
            enter = if (env.reducedMotion) EnterTransition.None else MotionTokens.Recipe.statusPillEnter(),
            exit = ExitTransition.None,
            label = "courseStatusPill"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(KebiaoSpacing.X2))
                if (spec != null) StatusPill(spec = spec)
            }
        }
    }
}

/** 状态的中文播报名（供 TalkBack 与 pill 共用口径）。 */
fun stateLabelOf(state: CourseCardState): String = when (state) {
    CourseCardState.ONGOING -> "正在进行中"
    CourseCardState.FINISHED -> "已结束"
    CourseCardState.UPCOMING_SOON -> "即将开始"
    CourseCardState.LATER -> "稍后"
    CourseCardState.PENDING -> "时间地点待定"
    CourseCardState.ONLINE -> "线上课程"
    CourseCardState.NOT_THIS_WEEK -> "本周不上"
}
