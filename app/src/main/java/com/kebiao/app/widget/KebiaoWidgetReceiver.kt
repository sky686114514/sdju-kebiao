package com.kebiao.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 小组件宿主。
 *
 * 只做一件事：把 [KebiaoWidget] 交给框架。所有数据与刷新逻辑在 [WidgetUpdater] 与
 * [KebiaoWidget] 内，宿主本身不含任何业务（入口只装配）。
 *
 * 清单里声明两个尺寸（4x2 / 4x4，D-6），二者共用同一个 `GlanceAppWidget` 实例，
 * 布局按实际高度自适应（[KebiaoWidget] 中按 `LocalSize` 决定可见行数），
 * 避免"两套布局两种渲染差异"的长期维护成本。
 */
class KebiaoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = KebiaoWidget()
}
