package com.kebiao.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/* 课刻 · 圆角 Token（来源 docs/design-tokens.kt §4 + docs/UIUX.md §3.3）
 * 卡片圆角上限 16dp；>= 24dp 属过度圆滑的 AI 特征，禁用。 */
object KebiaoShapes {

    val Current: Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),   // chip、状态标签
        small = RoundedCornerShape(8.dp),        // chip、状态标签
        medium = RoundedCornerShape(12.dp),      // 卡片、输入框、周网格单元
        large = RoundedCornerShape(16.dp),       // BottomSheet 顶部、分组容器
        extraLarge = RoundedCornerShape(20.dp)   // 对话框
    )

    /** 状态 pill、FAB、进度条、周次切换器。 */
    val Pill = RoundedCornerShape(999.dp)
}
