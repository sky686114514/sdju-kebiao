package com.kebiao.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.theme.KebiaoSpacing

/* =========================================================================
 * 分组标题（今日课程页的「待定」分组、周视图的日期列头等）
 *
 * 仪表盘精密风：左侧 16dp 图标 + 标题 + 贯穿到底的 1dp 细分隔线。
 * 不用卡片盒子，不用彩色左边框（禁止 border-left 色条）。
 * ========================================================================= */

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            KebiaoIcon(
                res = icon,
                contentDescription = null,
                size = IconSize.Inline,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(KebiaoSpacing.InlineIconGap))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(KebiaoSpacing.X3))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        if (trailing != null) {
            Spacer(Modifier.width(KebiaoSpacing.X2))
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}
