package com.kebiao.app.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons

/* =========================================================================
 * 底部导航栏
 *
 * 选中态用 Material Symbols 的 FILL 轴切换（FILL=0 描边 -> FILL=1 实心），
 * 不换图标、不加下划线、不换颜色语义之外的任何东西 —— 这是 M3 规范的表达方式。
 *
 * 导航 chrome 的选中色不计入「每屏 accent <= 2 处」的内容区预算（UIUX §3.1）。
 * ========================================================================= */

@Composable
fun KebiaoBottomBar(
    selected: TopLevelTab,
    onSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        TopLevelTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    KebiaoIcon(
                        res = iconFor(tab, selected = isSelected),
                        contentDescription = null,
                        size = IconSize.Standard,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(text = tab.label, style = MaterialTheme.typography.labelMedium)
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

private fun iconFor(tab: TopLevelTab, selected: Boolean): Int = when (tab) {
    TopLevelTab.TODAY ->
        if (selected) KebiaoIcons.CalendarTodayFill else KebiaoIcons.CalendarToday
    TopLevelTab.WEEK ->
        if (selected) KebiaoIcons.CalendarViewWeekFill else KebiaoIcons.CalendarViewWeek
    TopLevelTab.SETTINGS ->
        if (selected) KebiaoIcons.SettingsFill else KebiaoIcons.Settings
}
