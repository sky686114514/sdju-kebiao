package com.kebiao.app.feature.import

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalSemanticColors

/* =========================================================================
 * 登录过程的外壳：提示条 + 诊断 + 引导 + 终态（真机首验 H3 / 第二轮"卡住"的产物）
 *
 * 为什么必须存在：新版教务门户是单页应用，登录成功后停在门户首页是**正常行为**，
 * 不是失败。用户需要一个明确动作（自己点进「我的课表」）和一个兜底动作
 * （打开课表后点「读取课表」），否则页面看上去就是"卡住了"。
 *
 * 诚实纪律：文案不得声称"登录成功后会自动读取课表" —— 我们无法保证 SPA 跳转
 * 一定被捕获、也无法保证抽取脚本一定认得真实 DOM。因此这里把"需要你点什么"讲清楚，
 * 并把当前页面地址、探针结论、扫描计数直接显示出来。
 *
 * ## 终态（第二轮真机的直接成因）
 * 抽取脚本认出课表却解析出 0 条时，既不产载荷也不报错 —— UI 会永远停在"正在读取"。
 * 因此 `stalled` 为真时必须给出**明确的下一步**，并让用户能把真实结构复制出来给我们。
 * ========================================================================= */

/**
 * WebView 侧的诊断上报与一次性指令通道。
 *
 * 全部是「上报 / 指令」，不含任何流程判定 —— 判定仍在数据层的 `ImportStateMachine`。
 * 收成一个对象而不是散成 11 个参数，是为了不让 `ImportScreen` 的签名炸开。
 */
data class ImportLoginBridge(
    val onUrlChanged: (url: String) -> Unit,
    val onProbe: (probeJson: String) -> Unit,
    val onScheduleEntry: (entryJson: String) -> Unit,
    val onEntryClick: (stepJson: String) -> Unit,
    val onExtractNow: () -> Unit,
    val onOpenSchedule: (href: String) -> Unit,
    val onEntryLoaded: () -> Unit,
    val onCopyStructure: () -> Unit,
    val onStructureDumped: (dumpJson: String) -> Unit,
    val onStructureDumpConsumed: () -> Unit,
    val onExtractFailure: (failureJson: String) -> Unit,
    val onPollExhausted: () -> Unit
)

/**
 * 登录阶段的完整内容：提示条 + WebView。
 *
 * 单独成体（而不是写在 `ImportScreen` 的分支里）是为了守住「单文件 ≤300 行」——
 * `ImportScreen` 已经有六个步骤分支。
 */
@Composable
fun ImportLoginStage(
    state: ImportUiState.LoggingIn,
    loginUrl: String,
    extractionScript: String,
    onPayloadExtracted: (String) -> Unit,
    bridge: ImportLoginBridge,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LoginChrome(
            currentUrl = state.currentUrl,
            probeHint = state.probeHint,
            entryHref = state.entryHref,
            stalled = state.stalled,
            probeSawSchedule = state.probeSawSchedule,
            scanStats = state.scanStats,
            entryClickHint = state.entryClickHint,
            structureDump = state.structureDump,
            onExtractNow = bridge.onExtractNow,
            onOpenSchedule = bridge.onOpenSchedule,
            onCopyStructure = bridge.onCopyStructure,
            onStructureDumpConsumed = bridge.onStructureDumpConsumed
        )
        WebViewLoginScreen(
            startUrl = loginUrl,
            extractionScript = extractionScript,
            onPageFinished = { },
            onExtract = onPayloadExtracted,
            onPageUrlChanged = bridge.onUrlChanged,
            onProbe = bridge.onProbe,
            onScheduleEntry = bridge.onScheduleEntry,
            onEntryClick = bridge.onEntryClick,
            extractTick = state.extractTick,
            pendingUrl = state.pendingUrl,
            onPendingUrlLoaded = bridge.onEntryLoaded,
            dumpTick = state.dumpTick,
            onStructureDumped = bridge.onStructureDumped,
            onExtractFailure = bridge.onExtractFailure,
            onPollExhausted = bridge.onPollExhausted,
            // 用 weight 而不是 fillMaxSize：提示条先量自身高度，WebView 再吃掉剩下空间。
            // 写 fillMaxSize 会让它按整列高度测量，底部被提示条挤出屏幕（点不到）。
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
private fun LoginChrome(
    currentUrl: String?,
    probeHint: String?,
    entryHref: String?,
    stalled: Boolean,
    probeSawSchedule: Boolean,
    scanStats: String?,
    entryClickHint: String?,
    structureDump: String?,
    onExtractNow: () -> Unit,
    onOpenSchedule: (href: String) -> Unit,
    onCopyStructure: () -> Unit,
    onStructureDumpConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val context = LocalContext.current

    // 结构导出结果 -> 剪贴板。剪贴板要 Context，所以这一步留在 UI，VM 只负责搬运文本。
    LaunchedEffect(structureDump) {
        val text = structureDump ?: return@LaunchedEffect
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("课表页面结构", text))
            Toast.makeText(context, "已复制页面结构", Toast.LENGTH_SHORT).show()
        }
        onStructureDumpConsumed()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(
                start = KebiaoSpacing.ScreenGutter,
                end = KebiaoSpacing.ScreenGutter,
                top = KebiaoSpacing.X2,
                bottom = KebiaoSpacing.X2
            )
    ) {
        if (stalled) {
            Text(
                text = if (probeSawSchedule) {
                    "已找到课表表格，但没能解析出课程。点「复制页面结构」把结构发给我们"
                } else {
                    "没找到课表表格，请确认已打开「我的课表」页面"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.warn
            )
            Spacer(Modifier.height(KebiaoSpacing.X2))
        } else {
            // 自动点击一旦有进展就用它替换静态引导：用户想知道 App 替他点了什么，
            // 而不是再读一遍"请自己点进我的课表"。
            val guidance = entryClickHint
                ?: "登录成功后，请在下方页面自己点进「我的课表」，" +
                    "打开后再点一次「读取课表」"
            Text(
                text = guidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(KebiaoSpacing.X2))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)
        ) {
            Button(onClick = onExtractNow) {
                KebiaoIcon(
                    res = KebiaoIcons.FileDownload,
                    contentDescription = null,
                    size = IconSize.Inline,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "读取课表",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = KebiaoSpacing.X2)
                )
            }
            if (entryHref != null) {
                TextButton(onClick = { onOpenSchedule(entryHref) }) {
                    KebiaoIcon(
                        res = KebiaoIcons.CalendarViewWeek,
                        contentDescription = null,
                        size = IconSize.Inline,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "进入课表",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = KebiaoSpacing.X2)
                    )
                }
            }
        }
        Spacer(Modifier.height(KebiaoSpacing.X1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append(describeLocation(currentUrl))
                    if (!probeHint.isNullOrBlank()) append(" · ").append(probeHint)
                    if (!scanStats.isNullOrBlank()) append(" · ").append(scanStats)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(KebiaoSpacing.X1))
            TextButton(onClick = onCopyStructure) {
                KebiaoIcon(
                    res = KebiaoIcons.FileUpload,
                    contentDescription = null,
                    size = IconSize.Inline,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "复制页面结构",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = KebiaoSpacing.X1)
                )
            }
        }
    }
}

/**
 * 把当前页面地址压成「主机 + 路径」的一行。
 * 真机上判断"停在门户首页还是进了课表"最快的取证信息。
 * 注意：不带 query —— 会话参数不该出现在要被复制出去的文本里。
 */
private fun describeLocation(url: String?): String {
    if (url.isNullOrBlank()) return "正在打开教务系统"
    return try {
        val uri = Uri.parse(url)
        val host = uri.host
        val path = uri.path
        when {
            host.isNullOrBlank() -> url.take(64)
            path.isNullOrBlank() -> host
            else -> host + path
        }
    } catch (_: Exception) {
        url.take(64)
    }
}
