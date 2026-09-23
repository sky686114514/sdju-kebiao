package com.kebiao.app.feature.import

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.AppType
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalSemanticColors

/* =========================================================================
 * 导入核对视图（UIUX §8 / Spec §4.3 强制约束 3）
 *
 * 「导入识别错 -> 旷课」是竞品头号差评。所以提交前必须让用户看到：
 * 抓到了几门课、每门课有哪些周次 / 教室 / 教师，以及解析告警。
 *
 * 告警必须显式列出（AC-04：解析失败不得静默返回空集）。
 * ========================================================================= */

@Composable
fun ImportVerifyView(
    preview: ImportPreview,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = KebiaoSpacing.ScreenGutter,
            end = KebiaoSpacing.ScreenGutter,
            top = KebiaoSpacing.X3,
            bottom = KebiaoSpacing.X8
        ),
        verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)
    ) {
        item {
            Text(
                text = "请核对抓取到的课表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Text(
                text = "共 ${preview.courseCount} 门课 · ${preview.sessionCount} 条上课安排",
                style = AppType.meta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 抽取证据卡片：真机第六轮"只解析出 1 门课"的取证入口。
        // 卡片置于"共 N 门课"之下、解析告警之上 —— 它回答的是"为什么只有 1 门"，
        // 与告警的"哪些周次没读懂"是两件事，不能混在一段里。
        if (preview.readStats != null || preview.cellEvidence != null) {
            item {
                val clipboard = LocalClipboardManager.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(KebiaoSpacing.X2),
                    verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)
                ) {
                    if (preview.readStats != null) {
                        Text(
                            text = preview.readStats!!,
                            style = AppType.meta(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (preview.cellEvidence != null) {
                        TextButton(
                            onClick = {
                                // Compose 1.12：`ClipboardManager` 的写入口已由
                                // `setClipEntry` 改名为 `setClip`（API 已移除旧名）。
                                clipboard.setClip(
                                    ClipEntry(ClipData.newPlainText("课表格子证据", preview.cellEvidence!!))
                                )
                            }
                        ) {
                            Text("复制逐块证据")
                        }
                    }
                }
            }
        }

        if (preview.hasWarnings) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = KebiaoSpacing.X2),
                    verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KebiaoIcon(
                            res = KebiaoIcons.Warning,
                            contentDescription = null,
                            size = IconSize.Inline,
                            tint = semantic.warn
                        )
                        Spacer(Modifier.width(KebiaoSpacing.InlineIconGap))
                        Text(
                            text = "${preview.warnings.size} 条内容没读懂，请人工确认",
                            style = MaterialTheme.typography.titleSmall,
                            color = semantic.warn
                        )
                    }
                    preview.warnings.forEach { warning ->
                        Text(
                            text = "${warning.courseName}：「${warning.raw}」-> ${warning.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(items = preview.courses, key = { it.name }) { course ->
            Column(Modifier.fillMaxWidth().padding(vertical = KebiaoSpacing.X2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${course.sessionCount} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                course.detailLines.forEach { line ->
                    Text(
                        text = line,
                        style = AppType.meta(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/** 核对视图底部的操作区：确认导入 / 返回修改。 */
@Composable
fun ImportVerifyActions(
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter, vertical = KebiaoSpacing.X3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("返回修改") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onConfirm) {
            Text(
                text = "确认导入",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
