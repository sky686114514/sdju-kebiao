package com.kebiao.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.repository.ScheduleRepository
import com.kebiao.app.di.AppContainer
import com.kebiao.app.domain.model.TodaySchedule
import com.kebiao.app.domain.schedule.ScheduleCalculator
import com.kebiao.app.domain.schedule.SemesterFormatting

/**
 * 桌面小组件（Glance，ADR-005；总监裁决 D-6：4x2 为主，同版本内提供 4x4）。
 *
 * ## 数据来源
 *
 * 只读今日课表，**复用与主界面完全相同的一份求值结果**（`ScheduleCalculator.todaySchedule`），
 * 因此不存在"小组件和 App 显示不一致"的可能。
 *
 * ## 刷新时机（ARCHITECTURE.md 第 9.2 节）
 *
 * 数据变更 / 学期切换 / 跨零点 / WorkManager 周期兜底 / App 回到前台，统一由
 * [WidgetUpdater] 触发 `updateAll`。小组件自己不做轮询。
 *
 * ## 颜色纪律（P0 规则 2）
 *
 * 本文件**不出现任何色值**：结构色一律取 `GlanceTheme.colors`（Material3 Token），
 * 课程识别色来自主题层实现的 [AccentPaletteProvider]；调色板缺失时不画识别环，
 * 由 [WidgetUpdater] 记录事件，不猜颜色。
 *
 * ## Glance 约束（不要对它抱不切实际的期待）
 *
 * Glance 走 RemoteViews 渲染路径，只支持它提供的基础组件；没有动画、没有任意 Modifier。
 * 因此小组件不做过渡动画 —— 主界面的动画预算（Spec 第 8.1 节）不适用于小组件。
 */
class KebiaoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = AppContainer.from(context)
        val schedule = loadTodaySchedule(container)
        val palette = container.accentPaletteProvider.palette()

        provideContent {
            GlanceTheme {
                WidgetContent(schedule = schedule, palette = palette)
            }
        }
    }

    /**
     * 一次性读取"今天"。
     *
     * 这里刻意不用 Flow：小组件是"取一次、渲染一次"的模型，
     * 持续订阅既无必要（RemoteViews 不会重组），也会白耗电。
     */
    private suspend fun loadTodaySchedule(container: AppContainer): TodaySchedule {
        val timeProvider: TimeProvider = container.timeProvider
        val today = timeProvider.today()
        return when (val snapshot = container.scheduleRepository.snapshotForScheduling()) {
            is ScheduleRepository.SnapshotResult.NoSemester -> TodaySchedule.notImported(today)
            is ScheduleRepository.SnapshotResult.Failed -> TodaySchedule.notImported(today)
            is ScheduleRepository.SnapshotResult.Loaded -> ScheduleCalculator.todaySchedule(
                date = today,
                semester = snapshot.semester,
                sessions = snapshot.sessions,
                coursesById = snapshot.courses,
                now = timeProvider.now(),
                hasAnySession = snapshot.sessions.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun WidgetContent(schedule: TodaySchedule, palette: List<Color>) {
    val colors = GlanceTheme.colors
    val size = LocalSize.current
    val maxRows = maxRowsFor(size.height.value)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .padding(12.dp),
    ) {
        Text(
            text = headerText(schedule),
            style = TextStyle(color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        if (schedule.items.isEmpty()) {
            Text(
                text = emptyText(schedule),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return@Column
        }

        schedule.items.take(maxRows).forEach { item ->
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(bottom = 4.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // 课程识别环：8dp 圆点。调色板缺失时留一个等宽占位，保持左边距对齐。
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(accentColorOf(palette, item.accentIndex, colors.onSurfaceVariant)),
                    content = {},
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = listOfNotNull(
                            item.startTime?.toString(),
                            item.courseName,
                        ).joinToString("  "),
                        style = TextStyle(color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = SemesterFormatting.location(item),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

/**
 * 识别环索引 -> 颜色。
 *
 * 调色板长度不足 8（主题层尚未接线）时退化为主题的次要前景色，
 * **不去猜具体色值**：颜色只允许来自 Token（D-1）。
 */
private fun accentColorOf(palette: List<Color>, index: Int, fallback: ColorProvider): ColorProvider =
    if (palette.size >= ACCENT_SLOT_COUNT) {
        ColorProvider(palette[index.coerceIn(0, ACCENT_SLOT_COUNT - 1)])
    } else {
        fallback
    }

/** 组件高度 -> 可见课次行数（4x2 约显示 2-3 行，4x4 显示全天）。 */
private fun maxRowsFor(heightDp: Float): Int = when {
    heightDp < 90f -> 1
    heightDp < 140f -> 2
    heightDp < 200f -> 4
    else -> MAX_ROWS
}

private fun headerText(schedule: TodaySchedule): String {
    val date = schedule.date
    val week = schedule.weekOfSemester
    val label = if (week == null) {
        SemesterFormatting.dateLabel(date)
    } else {
        "${SemesterFormatting.dateLabel(date)}  ${SemesterFormatting.weekAndWeekday(week, date)}"
    }
    return label
}

/** 与主界面完全一致的三种"没课"文案，禁止写"暂无数据"（Spec 第 7 节）。 */
private fun emptyText(schedule: TodaySchedule): String = when (schedule.emptyReason) {
    com.kebiao.app.domain.model.TodayEmptyReason.NOT_IMPORTED -> "还没有课表，打开 App 去导入"
    com.kebiao.app.domain.model.TodayEmptyReason.OUTSIDE_SEMESTER -> "今天在学期之外"
    else -> "今天没有课"
}

private const val ACCENT_SLOT_COUNT = 8
private const val MAX_ROWS = 8
