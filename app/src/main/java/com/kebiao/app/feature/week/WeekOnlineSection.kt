package com.kebiao.app.feature.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.ui.components.ONLINE_LABEL
import com.kebiao.app.ui.components.SectionHeader
import com.kebiao.app.ui.components.formatLocation
import com.kebiao.app.ui.components.formatTimeRange
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoShapes
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalSemanticColors
import com.kebiao.app.ui.theme.courseColorFor

/* =========================================================================
 * 周视图 · 线上 / 不排座分区（AC-51）
 *
 * 线上课与「无固定星期 / 无固定时间地点」的课**不得硬塞进网格**：
 * 塞进去要么占一个假格位、要么让整列高度失真。它们在 domain 侧已被
 * `WeekScheduleBuilder` 分到 `unscheduled`，这里只负责渲染这一分区。
 *
 * 从 WeekScreen 拆分出来的原因：单文件 <= 300 行的硬约束，且这块与网格渲染无关，
 * 是独立的展示单元。
 * ========================================================================= */

@Composable
internal fun OnlineAndUnplacedSection(
    items: List<SessionOccurrence>,
    onCourseClick: (Long) -> Unit
) {
    Column(modifier = Modifier.padding(top = KebiaoSpacing.SectionGap)) {
        SectionHeader(title = "线上 / 不排座", icon = KebiaoIcons.Wifi)
        Spacer(Modifier.height(KebiaoSpacing.X2))
        items.forEach { occurrence ->
            OnlineRow(occurrence = occurrence, onCourseClick = onCourseClick)
        }
    }
}

@Composable
private fun OnlineRow(
    occurrence: SessionOccurrence,
    onCourseClick: (Long) -> Unit
) {
    val courseColor = courseColorFor(occurrence.accentIndex)
    val semantic = LocalSemanticColors.current
    val location = formatLocation(occurrence) ?: if (occurrence.isOnline) ONLINE_LABEL else "以学院通知为准"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter)
            .clip(KebiaoShapes.Current.small)
            .background(courseColor.tint.copy(alpha = 0.55f))
            .padding(KebiaoSpacing.X3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(KebiaoSpacing.CourseDot)
                .clip(CircleShape)
                .background(courseColor.value)
        )
        Spacer(Modifier.width(KebiaoSpacing.X3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = occurrence.courseName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    val time = formatTimeRange(occurrence.startTime, occurrence.endTime)
                    if (time.isNotEmpty()) append("$time · ")
                    append(location)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (occurrence.isOnline) {
            Text(
                text = ONLINE_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = semantic.info
            )
        }
    }
    Spacer(Modifier.height(KebiaoSpacing.X2))
}
