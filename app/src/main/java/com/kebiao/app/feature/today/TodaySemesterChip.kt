package com.kebiao.app.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kebiao.app.ui.components.shortSemesterName
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens

/* =========================================================================
 * 学期 Chip（TopAppBar 右侧）
 *
 * UIUX §5.3 编排的第二拍：+100ms 学期名称竖向翻页 150ms
 * （旧的向上走，新的从下进）。AnimatedContent key = 学期名。
 * ========================================================================= */

@Composable
fun SemesterChip(
    semesterName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val env = LocalMotionEnvironment.current
    val label = semesterName?.let { shortSemesterName(it) } ?: "选择学期"

    AssistChip(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        label = {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    if (env.reducedMotion) {
                        androidx.compose.animation.EnterTransition.None togetherWith
                            androidx.compose.animation.ExitTransition.None
                    } else {
                        MotionTokens.Recipe.semesterChipIn() togetherWith
                            MotionTokens.Recipe.semesterChipOut()
                    }
                },
                label = "semesterChipFlip"
            ) { text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(KebiaoSpacing.X1))
                    KebiaoIcon(
                        res = KebiaoIcons.ExpandMore,
                        contentDescription = null,
                        size = IconSize.Inline,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
