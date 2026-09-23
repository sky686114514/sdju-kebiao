package com.kebiao.app.data.import

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.AuthReason
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.domain.model.ImportSourceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 导入状态机（ARCHITECTURE.md 第 7.6 节）。
 *
 * 状态机刻意放在数据层而不是 ViewModel 里：它的每个状态都对应一次真实的数据操作，
 * 与 Compose 无关，因此可以被单测覆盖。ViewModel 只负责把它映射成 `ImportUiState` 并渲染。
 *
 * ```
 * Idle -> (start) -> AwaitingLogin(仅 WebView) -> Fetching -> Parsing -> Preview
 *   Preview -> (confirm) -> Committing -> Done | Error
 *   任意阶段 -> Error(AppError) + retryable
 * ```
 *
 * 硬约束：
 *  1. **Preview 是强制环节**，`confirm()` 之前绝不写库（防"导入后才发现乱排"）；
 *  2. 失败一律进入 [ImportState.Error]，不静默回落到 Idle；
 *  3. 每一个 [AppError] 都在 [isRetryable] 中给出明确的可重试判定，不留"到底能不能重试"的含糊。
 */
class ImportStateMachine(
    private val source: ImportSource,
    private val mapper: ImportMapper,
    private val committer: ImportCommitter,
    private val eventLog: LocalEventLog,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)

    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** 取数 + 解析，停在 [ImportState.Preview] 等用户核对。 */
    suspend fun start(): ImportState {
        _state.value = if (source.kind == ImportSourceKind.WEBVIEW) ImportState.AwaitingLogin else ImportState.Fetching
        eventLog.append(event(name = LocalEvents.IMPORT_START, attributes = mapOf("source" to source.kind.name)))

        _state.value = ImportState.Fetching
        val raw = when (val fetched = source.fetch()) {
            is AppResult.Success -> fetched.value
            is AppResult.Failure -> return fail(fetched.error)
        }

        _state.value = ImportState.Parsing
        val plan = when (val mapped = mapper.map(raw)) {
            is AppResult.Success -> mapped.value
            is AppResult.Failure -> return fail(mapped.error)
        }

        return ImportState.Preview(preview = plan.toPreview(), plan = plan).also { _state.value = it }
    }

    /**
     * 用户在核对视图确认后写库。
     *
     * [payloadRef] 是显式覆盖项；为空时回落 [ImportPlan.payloadRef] ——
     * 计划已从 `RawSchedule` 透传原始载荷路径，调用方不必自己再存一份载荷。
     */
    suspend fun confirm(plan: ImportPlan, payloadRef: String? = plan.payloadRef): ImportState {
        _state.value = ImportState.Committing
        val effectivePayloadRef = payloadRef ?: plan.payloadRef
        return when (val committed = committer.commit(plan, source.kind, effectivePayloadRef)) {
            is AppResult.Success -> ImportState.Done(committed.value).also { _state.value = it }
            is AppResult.Failure -> fail(committed.error)
        }
    }

    /** 用户取消：回到 Idle，并释放数据源资源。 */
    suspend fun cancel() {
        source.dispose()
        _state.value = ImportState.Idle
    }

    /** 从 Error 回到 Idle，让用户可以换一种方式重试（例如从 WebView 改为导入 JSON 文件）。 */
    fun reset() {
        _state.value = ImportState.Idle
    }

    suspend fun dispose() {
        source.dispose()
    }

    private fun fail(error: AppError): ImportState.Error =
        ImportState.Error(error = error, retryable = isRetryable(error)).also { state ->
            _state.value = state
            eventLog.append(
                event(
                    name = LocalEvents.IMPORT_FAIL,
                    attributes = mapOf("message" to error.userMessage),
                )
            )
        }

    private fun event(name: String, attributes: Map<String, String>): LocalEvent =
        LocalEvent(name = name, timestampMillis = nowMillis(), attributes = attributes)

    /** 可重试判定：网络/解析/存储/未知可重试；权限与"数据不存在"要先改变外部条件才能重试。 */
    private fun isRetryable(error: AppError): Boolean = when (error) {
        is AppError.Network -> true
        is AppError.Parse -> true
        is AppError.Storage -> true
        is AppError.Unknown -> true
        is AppError.AuthFailed -> error.reason != AuthReason.NEEDS_PHONE_BINDING
        is AppError.Permission -> false
        is AppError.NotFound -> false
    }
}

/** 导入流程的完整状态集合。UI 必须为每个分支提供对应界面（ARCHITECTURE.md 第 12 节）。 */
sealed interface ImportState {

    data object Idle : ImportState

    /** 仅 WebView 路径使用：等真人在系统 WebView 里完成 SSO 登录（含手机绑定 / 短信码）。 */
    data object AwaitingLogin : ImportState

    data object Fetching : ImportState

    data object Parsing : ImportState

    /** 强制核对环节：展示课程数 / 周次 / 教室 / 教师与全部提示项。 */
    data class Preview(val preview: ImportPreview, val plan: ImportPlan) : ImportState

    data object Committing : ImportState

    data class Done(val result: ImportResult) : ImportState

    data class Error(val error: AppError, val retryable: Boolean) : ImportState
}
