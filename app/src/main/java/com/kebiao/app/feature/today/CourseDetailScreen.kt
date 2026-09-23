package com.kebiao.app.feature.today

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.components.CourseCardState
import com.kebiao.app.ui.components.EmptyState
import com.kebiao.app.ui.components.KebiaoGlassTopAppBar
import com.kebiao.app.ui.components.ONLINE_LABEL
import com.kebiao.app.ui.components.StatusPill
import com.kebiao.app.ui.components.courseCardStateOf
import com.kebiao.app.ui.components.minutesUntilStart
import com.kebiao.app.ui.components.statusPillSpecFor
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.courseColorFor

/* =========================================================================
 * 课程详情（层级页，shared-axis X 从右侧推入）
 *
 * 内容只呈现**这门课本身的事实**：课程名 / 时间 / 节次 / 周次 / 地点 / 教师 / 备注。
 * 若该课次命中今天，额外显示当次状态 pill（进行中 / N 分钟后 / 已结束）。
 *
 * 配色遵守课程配色铁律：色只出现在 8dp 圆点与标题左侧的识别标识上，
 * 不做整块彩色背景（那是 AI 模板味，也是本项目点名禁止的写法）。
 * ========================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    state: CourseDetailUiState,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(contentPadding)) {
        KebiaoGlassTopAppBar(
            title = {
                Text(
                    text = "课程详情",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

        when {
            state.loading -> Spacer(Modifier.fillMaxSize())

            !state.found -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                EmptyState(
                    icon = KebiaoIcons.EventBusy,
                    title = "这条安排已经不在当前学期里",
                    subtitle = "它可能属于已切换掉的学期，或已被重新导入的课表替换。",
                    actionLabel = "返回",
                    onAction = onBack
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = KebiaoSpacing.ScreenGutter)
            ) {
                Header(state)
                Spacer(Modifier.height(KebiaoSpacing.X4))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(KebiaoSpacing.X4))

                InfoRow(icon = KebiaoIcons.Schedule, label = "时间", value = state.timeLabel)
                state.periodLabel?.let { InfoRow(icon = KebiaoIcons.Tag, label = "节次", value = it) }
                state.weekdayLabel?.let { InfoRow(icon = KebiaoIcons.DateRange, label = "星期", value = it) }
                InfoRow(icon = KebiaoIcons.EventRepeat, label = "周次", value = state.weeksLabel)
                InfoRow(
                    icon = if (state.isOnline) KebiaoIcons.Wifi else KebiaoIcons.LocationOn,
                    label = "地点",
                    value = when {
                        state.isOnline -> listOfNotNull(ONLINE_LABEL, state.room?.takeIf { it.isNotBlank() })
                            .joinToString(" · ")
                        !state.room.isNullOrBlank() -> state.room
                        else -> "以学院通知为准"
                    }
                )
                state.campus?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = KebiaoIcons.School, label = "校区", value = it)
                }
                InfoRow(
                    icon = KebiaoIcons.Person,
                    label = "教师",
                    value = state.teacher?.takeIf { it.isNotBlank() } ?: "教师待定"
                )
                state.courseCode?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = KebiaoIcons.Tag, label = "课程代码", value = it)
                }
                state.remark?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = KebiaoIcons.Help, label = "备注", value = it)
                }

                Spacer(Modifier.height(KebiaoSpacing.X16))
            }
        }
    }
}

/** 标题区：8dp 课程色圆点 + 课程名 + （命中今天时）当次状态 pill。 */
@Composable
private fun Header(state: CourseDetailUiState) {
    val courseColor = courseColorFor(state.accentIndex)
    val occurrence = state.todayOccurrence
    val cardState: CourseCardState? = occurrence?.let { courseCardStateOf(it, state.now) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = KebiaoSpacing.X3)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(KebiaoSpacing.CourseDot)
                    .clip(CircleShape)
                    .background(courseColor.value)
            )
            Spacer(Modifier.width(KebiaoSpacing.X3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.courseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.semesterName.isNotBlank()) {
                    Spacer(Modifier.height(KebiaoSpacing.X1))
                    Text(
                        text = state.semesterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (cardState != null) {
            Spacer(Modifier.height(KebiaoSpacing.X3))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)
            ) {
                val spec = statusPillSpecFor(
                    state = cardState,
                    minutesUntilStart = if (cardState == CourseCardState.UPCOMING_SOON) {
                        minutesUntilStart(occurrence, state.now)
                    } else {
                        null
                    }
                )
                if (spec != null) StatusPill(spec = spec)
                if (cardState == CourseCardState.ONGOING || cardState == CourseCardState.FINISHED) {
                    Text(
                        text = formatTodayHint(cardState),
                        style = AppType.meta(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatTodayHint(state: CourseCardState): String = when (state) {
    CourseCardState.ONGOING -> "今天这节课正在进行中"
    CourseCardState.FINISHED -> "今天这节课已经下课"
    else -> ""
}

/** 信息行：16dp 线性图标 + 标签 + 值。分组用分隔线而非卡片盒子。 */
@Composable
private fun InfoRow(icon: Int, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KebiaoSpacing.X2),
        verticalAlignment = Alignment.Top
    ) {
        KebiaoIcon(
            res = icon,
            contentDescription = null,
            size = IconSize.Compact,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(KebiaoSpacing.X3))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 详情页内部使用的课程识别色（供预览与测试断言，不参与布局常量）。 */
internal val CourseDetailHeaderShape = KebiaoShapes.Current.medium
