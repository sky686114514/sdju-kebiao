package com.kebiao.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.theme.BlurSupport
import com.kebiao.app.ui.theme.glassSurfaceAlpha

/* =========================================================================
 * 全项目唯一的顶栏（任务②：顶栏毛玻璃）
 *
 * 六个屏此前各写了一份 `TopAppBar(containerColor = background)`，改玻璃不能在六个地方
 * 各贴一遍 alpha 魔法数字 —— 分档口径（API 31+ / 以下）只允许存在于
 * [glassSurfaceAlpha] 一处。
 *
 * 【为什么是「半透明近似」而不是真模糊】
 *  `Modifier.blur` 模糊的是**节点自身**的绘制内容。真要模糊顶栏背后的东西，只能把整屏
 *  内容再组合一遍并 clip 到顶栏高度 —— 那是重复组合 + 双份状态，明确不采用。
 *  而本 App 六个屏的顶栏与内容都是 Column 上下排布、互不重叠，顶栏背后本来就只有一层
 *  页面背景色，即便真做模糊也糊不出任何东西。所以这里取标准替代方案：
 *  半透明 surface + 1dp 底部分隔线。
 *
 * 关于「底部渐隐 scrim」：scrim 只有在**有内容从栏下滚过**时才可见。本 App 没有沉浸
 *  式滚动（内容不与顶栏重叠），一条独立占位的渐隐带背后只有纯色背景，画了也看不见；
 *  若硬把它叠在内容上，则会永久地在第一张课程卡顶部压出一条洗白的带子。因此这里只留
 *  1dp 分隔线 —— 若后续要做「内容滚到栏下」，玻璃才真正成立，届时再补 scrim。
 *
 * 降级：API < 31 时 [glassSurfaceAlpha] 自动取更实的 0.94，保证标题文字对比度，
 * 不会出现「模糊没生效 + 字糊在彩色课程卡上」。
 *
 * 无动画：本组件是静态的，不含任何过渡，天然满足 reducedMotion / lowRam 约束。
 * ========================================================================= */

/**
 * @param title 标题内容（多数屏是单行 Text，今天页是「周次 + 日期」两行）。
 * @param actions 右侧操作区；TopAppBar 内部是 Row，这里传普通 composable 即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KebiaoGlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val containerAlpha = glassSurfaceAlpha(BlurSupport.available)

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = scheme.surface.copy(alpha = containerAlpha),
                // 无沉浸式滚动，也就没有 scrolled 态；两档取同一个值，避免出现
                // 「滚一下顶栏突然变实」这种没人要求的跳变。
                scrolledContainerColor = scheme.surface.copy(alpha = containerAlpha),
                titleContentColor = scheme.onSurface,
                navigationIconContentColor = scheme.onSurfaceVariant,
                actionIconContentColor = scheme.onSurfaceVariant
            )
        )
        // 1dp 分隔：半透明顶栏与内容之间必须有边界，否则滚动时会读成「内容缺了一块」。
        HorizontalDivider(thickness = 1.dp, color = scheme.outlineVariant)
    }
}
