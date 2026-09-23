package com.kebiao.app.data.import

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.ParseStage
import com.kebiao.app.domain.model.ImportSourceKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * WebView 中继导入源（主路径 c，ADR-003）。
 *
 * ## 与 WebView 的契约（关键，别写错）
 *
 * 本类**不接触 WebView**，也不注册任何 JS 接口。数据流是单向的：
 *
 * ```
 * WebViewLoginScreen（UI 层，前端负责）
 *   -> 真人完成 SSO 登录（含手机绑定 / 短信验证码）
 *   -> evaluateJavascript(抽取脚本) { json -> scope.launch { source.submit(json) } }
 *        ^ 只单向取值，不把原生能力暴露给页面
 * WebViewImportSource.submit(json)  -> Channel -> fetch() 解码为 RawSchedule
 * ```
 *
 * 三条不可违反的约束（Spec 第 4.3 节）：
 *  1. **不读取密码字段**：密码只在系统 WebView 的页面里，App 从不读它；
 *  2. **不注册 `@JavascriptInterface`**：页面内容来自校园网，属外部输入，按不可信处理；
 *  3. 超时 / 通道关闭必须给出**显式错误**，不允许挂死后静默返回空课表。
 */
class WebViewImportSource(
    private val payloadStore: PayloadStore,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : ImportSource {

    override val kind: ImportSourceKind = ImportSourceKind.WEBVIEW

    private val payloads: Channel<String> = Channel(capacity = Channel.BUFFERED)

    /** 由 WebView 侧在 `evaluateJavascript` 回调里调用，提交同源抽取到的 JSON 文本。 */
    suspend fun submit(payloadJson: String) {
        payloads.send(payloadJson)
    }

    /** 供 UI 在离开导入页时调用，让挂起的 [fetch] 尽快失败而不是等到超时。 */
    fun closeChannel() {
        payloads.close()
    }

    override suspend fun fetch(): AppResult<RawSchedule> = withContext(Dispatchers.IO) {
        val text = try {
            withTimeout(timeoutMillis) { receiveOnce(payloads) }
        } catch (e: TimeoutCancellationException) {
            // 只捕获"我们自己的超时"，不吞其他取消信号。
            return@withContext AppResult.Failure(
                AppError.Parse(ParseStage.TABLE_STRUCTURE, "等待课表数据超时（登录后未检测到可读取的课表页面）")
            )
        } catch (e: ClosedReceiveChannelException) {
            return@withContext AppResult.Failure(
                AppError.Parse(ParseStage.TABLE_STRUCTURE, "导入会话已关闭，未取到课表数据")
            )
        }

        if (text.isBlank()) {
            return@withContext AppResult.Failure(
                AppError.Parse(ParseStage.TABLE_STRUCTURE, "WebView 返回的课表数据为空")
            )
        }

        val payloadRef = try {
            payloadStore.save(prefix = "schedule-html-json", content = text)
        } catch (e: IOException) {
            null
        }

        ScheduleJsonCodec.decode(text = text, kind = kind, payloadRef = payloadRef)
    }

    override suspend fun dispose() {
        payloads.close()
    }

    private suspend fun receiveOnce(channel: ReceiveChannel<String>): String = channel.receive()

    companion object {
        /** 默认 5 分钟：足够真人在 WebView 里完成手机绑定与短信验证码。 */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L
    }
}
