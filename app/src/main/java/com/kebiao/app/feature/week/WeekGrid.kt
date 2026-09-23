package com.kebiao.app.feature.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 周视图网格（UIUX §7.3）
 *
 * 手机竖屏的关键决策：不横向硬塞 7 列（360dp 减 64dp 时间轴后 7 列只剩 39dp，
 * 课程名只能显示 2 个字）。改为**逐日列 + 横向 Pager**，一屏显示三列（约 92dp/列），
 * 左右滑到周四至周日。
 *
 * 时间轴 64dp **固定在 Pager 之外**，不随滑动移动。
 * 行高固定 88dp，所以网格高度 = 行数 × 88dp，可放进外层纵向滚动而不破坏 Pager 测量。
 *
 * Pager 参数（UIUX §5.7）：
 *  - 拖动 1:1 跟手
 *  - 吸附 spring(dampingRatio = 1.0, stiffness = 400)，NoBouncy，不用 M3 默认的 MediumBouncy
 *  - PagerSnapDistance.atMost(1) 禁止一次甩过多列
 * ========================================================================= */

/** 每行的固定高度。固定行高让时间轴与各日列严格水平对齐。 */
val WEEK_ROW_HEIGHT: Dp = 88.dp

/** 列头高度（周一 / 周二…）。时间轴在同高度处留空以保持对齐。 */
val WEEK_HEADER_HEIGHT: Dp = 40.dp

@Composable
fun WeekGrid(
    content: WeekUiState.Content,
    onCourseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialDay: Int = 0
) {
    val env = LocalMotionEnvironment.current
    val pagerState = rememberPagerState(
        initialPage = initialDay.coerceIn(0, 6),
        pageCount = { 7 }
    )
    val gridHeight = WEEK_HEADER_HEIGHT + WEEK_ROW_HEIGHT * content.rows.size

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val axisWidth = KebiaoSpacing.WeekAxisWidth
        // 一屏三列：可用宽度减固定时间轴后三等分
        val columnWidth = ((maxWidth - axisWidth) / 3).coerceAtLeast(64.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
        ) {
            WeekTimeAxis(
                rows = content.rows,
                modifier = Modifier.width(axisWidth)
            )

            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(columnWidth),
                // 一次手势最多翻一列；吸附用 NoBouncy spring，杜绝回弹
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapAnimationSpec = if (env.reducedMotion) {
                        MotionTokens.Recipe.quickColorChange()
                    } else {
                        MotionTokens.Spring.Spatial
                    }
                ),
                key = { it },
                modifier = Modifier.height(gridHeight)
            ) { page ->
                WeekDayColumn(
                    day = content.days.getOrNull(page),
                    rowCount = content.rows.size,
                    onCourseClick = onCourseClick
                )
            }
        }
    }
}

/** 时间轴：64dp 固定宽度，行高与日列一致，行间 1dp borderSoft 分隔。 */
@Composable
private fun WeekTimeAxis(
    rows: List<PeriodRow>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(WEEK_HEADER_HEIGHT))
        rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WEEK_ROW_HEIGHT),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = row.periodLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = row.startLabel,
                    style = AppType.axisTime(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = row.endLabel,
                    style = AppType.axisTime(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 列头：当天用 accentContainer 底纹，其余透明。 */
@Composable
internal fun WeekDayHeader(
    day: DayColumnData,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WEEK_HEADER_HEIGHT)
            .background(
                if (day.isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (day.isToday) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
