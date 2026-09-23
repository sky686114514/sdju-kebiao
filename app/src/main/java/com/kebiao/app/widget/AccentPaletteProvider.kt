package com.kebiao.app.widget

import com.kebiao.app.domain.model.Course

/**
 * 课程识别色的索引计算（Spec 第 8 节：`一色一课，课程代码稳定哈希取模 8`）。
 *
 * 计算本身在 [Course.accentIndex]（领域层，可单测）；这里只把"索引 -> 颜色"这一步
 * 留在主题层：**颜色必须来自 Design Token，不能出现在小组件代码里**（D-1）。
 *
 * 生产实现由主题层（`ui/theme`）提供，从 `design-tokens.json` 派生的 8 色课程色板映射为
 * Glance 可用的颜色列表。小组件拿不到该实现时**不猜颜色**，退化为不带识别环的纯文本行，
 * 并写 `error_occurred` 事件（见 [WidgetUpdater]），避免"颜色规则被静默绕过"。
 */
interface AccentPaletteProvider {

    /** 8 个课程识别色，顺序与 [Course.accentIndex] 的 0..7 一一对应。 */
    fun palette(): List<androidx.compose.ui.graphics.Color>
}

/** 未接线时的空实现：小组件不画识别环，但功能完整（信息不丢，只是少了颜色编码）。 */
object EmptyAccentPaletteProvider : AccentPaletteProvider {
    override fun palette(): List<androidx.compose.ui.graphics.Color> = emptyList()
}
