package com.kebiao.app.ui.theme

import com.kebiao.app.widget.AccentPaletteProvider

/* =========================================================================
 * 小组件课程识别色板 —— 主题层对 `widget.AccentPaletteProvider` 的生产实现
 *
 * 为什么必须存在：`AccentPaletteProvider` 的默认实现是 `EmptyAccentPaletteProvider`
 * （小组件退化为无识别环的纯文本，并写一条 `error_occurred`）。接线是 UI 模块的责任
 * （见 `AppContainer.accentPaletteProvider` 的注释：生产实现由主题层提供）。
 *
 * 为什么用 Light 一套：Glance 小组件绘制在**我们自己的** surface 上（不是窗口背景），
 * 该 surface 由小组件布局决定且为浅色，因此始终取 Light 色卡的 value 值。
 * 深色壁纸下不切换 —— 这是刻意选择：同一门课在课表与小组件上必须是同一个色号
 * （Spec 第 8 节「一色一课」），跨表层换色会破坏识别一致性。
 * ========================================================================= */

object CoursePaletteAccentPaletteProvider : AccentPaletteProvider {

    /**
     * 8 个识别色，顺序与 `Course.accentIndex` 的 0..7 严格一一对应。
     * 索引的计算在领域层（FNV-1a 取模 8），主题层只做「索引 -> 颜色」这一步。
     */
    override fun palette() = CoursePalette.valuesInIndexOrder()
}
