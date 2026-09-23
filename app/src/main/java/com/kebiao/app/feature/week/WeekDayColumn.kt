package com.kebiao.app.feature.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.ui.components.formatLocation
import com.kebiao.app.ui.components.ONLINE_LABEL
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalSemanticColors
import com.kebiao.app.ui.theme.courseColorFor

/* =========================================================================
 * 周视图 · 单日列
 *
 * 网格单元无卡片、无阴影：只有底色与文字（Density 5 的仪表盘模式）。
 *  - 有课：课程色 tint 底（约 10% 面积）+ 课程名（label 13/18 w500 onSurface）
 *          + 地点（caption 12/16 muted）；左上角 4dp 圆形色点
 *  - 无课：透明，不显示「无课」字样
 *  - 时间冲突：上下分栏各占一半高度，右上角 warning 图标（不用小三角）
 *
 * 周视图明确不做 stagger 入场（30 多个格子会糊成噪点）。
 * ========================================================================= */

@Composable
internal fun WeekDayColumn(
    day: DayColumnData?,
    rowCount: Int,
    onCourseClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (day == null) {
        Spacer(modifier.fillMaxWidth())
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        WeekDayHeader(day = day)
        // 行数与整页行表对齐：数据缺行时补透明占位，保证所有列水平对齐
        repeat(rowCount) { index ->
            val cell = day.cells.getOrNull(index)
            if (cell == null) {
                EmptyGridCell()
            } else {
                GridCell(cell = cell, onCourseClick = onCourseClick)
            }
        }
    }
}

/** 透明占位格，只画底部 1dp 分隔线。 */
@Composable
private fun EmptyGridCell() {
    val divider = LocalSemanticColors.current.borderSoft
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WEEK_ROW_HEIGHT)
            .drawBehind {
                drawLine(
                    color = divider,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    )
}

@Composable
private fun GridCell(
    cell: DayCell,
    onCourseClick: (Long) -> Unit
) {
    val divider = LocalSemanticColors.current.borderSoft
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WEEK_ROW_HEIGHT)
            .drawBehind {
                drawLine(
                    color = divider,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        if (cell.isEmpty) return@Box

        if (cell.isConflict) {
            ConflictCell(
                occurrences = cell.occurrences,
                onCourseClick = onCourseClick
            )
        } else {
            cell.primary?.let { occurrence ->
                CourseGridCell(
                    occurrence = occurrence,
                    modifier = Modifier.fillMaxSize(),
                    onCourseClick = onCourseClick
                )
            }
        }
    }
}

/** 单个课程格：tint 底 + 色点 + 课程名 + 地点。 */
@Composable
private fun CourseGridCell(
    occurrence: SessionOccurrence,
    modifier: Modifier = Modifier,
    onCourseClick: (Long) -> Unit
) {
    val courseColor = courseColorFor(occurrence.accentIndex)
    val location = formatLocation(occurrence) ?: if (occurrence.isOnline) ONLINE_LABEL else "地点待定"

    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(KebiaoShapes.Current.medium)
            .background(courseColor.tint)
            .clickable { onCourseClick(occurrence.sessionId) }
            .padding(KebiaoSpacing.X2),
        verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(courseColor.value)
            )
            Spacer(Modifier.width(KebiaoSpacing.X1))
            Text(
                text = occurrence.courseName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = location,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 时间冲突：上下分栏各占一半，右上角 warning 图标（不用小三角，那个看不见）。 */
@Composable
private fun ConflictCell(
    occurrences: List<SessionOccurrence>,
    onCourseClick: (Long) -> Unit
) {
    val semantic = LocalSemanticColors.current
    Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        occurrences.take(2).forEach { occurrence ->
            val courseColor = courseColorFor(occurrence.accentIndex)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 1.dp)
                    .clip(KebiaoShapes.Current.small)
                    .background(courseColor.tint)
                    .clickable { onCourseClick(occurrence.sessionId) }
                    .padding(horizontal = KebiaoSpacing.X1, vertical = 2.dp)
            ) {
                Text(
                    text = occurrence.courseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                KebiaoIcon(
                    res = KebiaoIcons.Warning,
                    contentDescription = "时间冲突",
                    size = IconSize.Inline,
                    tint = semantic.warn,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}
