package com.kebiao.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kebiao.app.core.AppError
import com.kebiao.app.ui.icons.IconSize
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing

/* =========================================================================
 * 错误状态（Spec §12 + UIUX §8）
 *
 * 每个 UiState 分支都要有对应 UI，不出现「错误了但界面没反应」。
 * 每个失败路径都有可重试入口；网络类额外提供「用上次数据」兜底。
 * 文案必须给出可读原因，禁止只写「出错了」。
 * ========================================================================= */

data class ErrorPresentation(
    val icon: Int,
    val title: String,
    val message: String,
    /** 网络类失败可以退回本地上次数据，避免用户看不了课表。 */
    val canUseCached: Boolean
)

/** AppError -> 展示模型。用 else 兜底，避免后端扩展错误类型时此处编译中断。 */
fun presentError(error: AppError): ErrorPresentation = when (error) {
    is AppError.Network -> ErrorPresentation(
        icon = KebiaoIcons.CloudOff,
        title = "网络连接失败",
        message = "无法连到教务系统，请检查网络后重试。已导入的课表不受影响。",
        canUseCached = true
    )
    is AppError.AuthFailed -> ErrorPresentation(
        icon = KebiaoIcons.Lock,
        title = "教务系统登录失效",
        message = "登录状态已过期，重新登录即可继续导入。已有课表不会被改动。",
        canUseCached = false
    )
    is AppError.Parse -> ErrorPresentation(
        icon = KebiaoIcons.Error,
        title = "课表解析失败",
        message = "页面结构和预期不一致，已保留原始数据用于核对。请重试或改用手动录入。",
        canUseCached = true
    )
    is AppError.Permission -> ErrorPresentation(
        icon = KebiaoIcons.NotificationsOff,
        title = "缺少必要权限",
        message = "提醒将无法送达，请到系统设置中授予权限。",
        canUseCached = false
    )
    is AppError.Storage -> ErrorPresentation(
        icon = KebiaoIcons.Error,
        title = "本地数据读写失败",
        message = "未能写入本地数据库，请确认存储空间充足后重试。",
        canUseCached = false
    )
    is AppError.NotFound -> ErrorPresentation(
        icon = KebiaoIcons.Inbox,
        title = "找不到课表数据",
        message = "当前学期还没有课表，先从教务系统导入一次。",
        canUseCached = false
    )
    else -> ErrorPresentation(
        icon = KebiaoIcons.Error,
        title = "出现未知问题",
        message = "操作未完成，请重试。若持续出现，可在设置页查看本地事件日志。",
        canUseCached = false
    )
}

@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    retryable: Boolean = true,
    onRetry: (() -> Unit)? = null,
    onUseCached: (() -> Unit)? = null
) {
    val presentation = presentError(error)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KebiaoSpacing.ScreenGutter, vertical = KebiaoSpacing.X10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KebiaoIcon(
            res = presentation.icon,
            contentDescription = null,
            size = IconSize.Standard,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(KebiaoSpacing.X4))
        Text(
            text = presentation.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(KebiaoSpacing.X2))
        Text(
            text = presentation.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(KebiaoSpacing.X5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (retryable && onRetry != null) {
                Button(onClick = onRetry) {
                    KebiaoIcon(
                        res = KebiaoIcons.Refresh,
                        contentDescription = null,
                        size = IconSize.Inline,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = KebiaoSpacing.X2)
                    )
                }
            }
            if (presentation.canUseCached && onUseCached != null) {
                Spacer(Modifier.width(KebiaoSpacing.X2))
                TextButton(onClick = onUseCached) {
                    Text(
                        text = "用上次数据",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
