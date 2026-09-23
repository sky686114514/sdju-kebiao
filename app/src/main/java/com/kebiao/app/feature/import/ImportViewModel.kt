package com.kebiao.app.feature.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.core.AppError
import com.kebiao.app.core.ParseStage
import com.kebiao.app.data.import.ImportPlan
import com.kebiao.app.data.import.ImportState
import com.kebiao.app.data.import.ImportStateMachine
import com.kebiao.app.data.import.WebViewImportSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* =========================================================================
 * 导入 ViewModel —— 「UI 适配器」，不是第二套状态机
 *
 * 流程编排（取数 -> 解析 -> 核对 -> 单事务提交）的唯一实现是数据层的
 * [ImportStateMachine]（它每个状态都对应一次真实数据操作，可被 JVM 单测覆盖）。
 * 本类只做两件事：
 *  1. 把 [ImportState] 映射成 [ImportUiState]（纯展示模型）；
 *  2. 把 WebView 抽到的载荷转交给 [WebViewImportSource]。
 *
 * 为什么不在 UI 层重写一遍流程：重写 = 两套口径 = 其中一套迟早与另一套不一致，
 * 这正是 `generated-code-failure-modes.md` 第 2 节点名的最昂贵失效（沉默逻辑错误）。
 *
 * ## 状态映射的一处刻意合并
 * `WebViewImportSource.fetch()` 会**一直挂起直到真人登录完成并注入载荷**，
 * 期间状态机的 state 是 [ImportState.Fetching]（它在 await 之前就把 state 置为 Fetching）。
 * 因此 `AwaitingLogin` 与 `Fetching` 在 UI 上都映射为 [ImportUiState.LoggingIn] ——
 * 对用户而言这两者都是「正在登录 / 读取中」，WebView 也仍需保持挂载（两个分支都渲染 WebView）。
 * 真正的解析阶段（[ImportState.Parsing]）才切到「正在解析课表」。
 *
 * ## payloadRef 链路（原登记的缺口已由数据层补上）
 * 数据层现已让 `ImportPlan` 携带 `payloadRef`（见 `ImportPlan.kt`），且
 * `ImportStateMachine.confirm(plan, payloadRef = plan.payloadRef)` 内部再回落
 * `payloadRef ?: plan.payloadRef`。因此本类调用 `confirm(plan, payloadRef = null)`
 * 不会丢值：`null` 会回落到 `plan.payloadRef`，`import_batches.payload_ref` 正常落库。
 * UI 侧纪律不变：这里不自行再存一份载荷，
 * 否则会出现两份"原始现场"互相不一致。
 * ========================================================================= */

class ImportViewModel(
    private val machine: ImportStateMachine,
    private val webViewSource: WebViewImportSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** 教务系统入口。UI 无需知道具体域名，只把它交给 WebView。 */
    val loginUrl: String get() = ImportWebView.LOGIN_URL

    /** 同源抽取脚本。WebView 在每次页面加载完成后执行它。 */
    val extractionScript: String get() = ImportWebView.EXTRACTION_SCRIPT

    /** 核对视图确认后要提交的计划；由 [ImportState.Preview] 捕获。 */
    private var pendingPlan: ImportPlan? = null

    /* ---- 登录现场诊断（真机首验 H1/H2/H3）----
     * 只服务于「提示条文案 + 卡住时取证」，不参与任何流程判定：
     * 判定权仍在数据层的状态机。状态机重发 LoggingIn 时必须把这些字段带过去，
     * 否则 AwaitingLogin -> Fetching 的切换会把现场抹掉。
     */
    private var currentUrl: String? = null
    private var probeHint: String? = null
    private var entryHref: String? = null
    private var extractTick = 0
    private var pendingUrl: String? = null
    private var dumpTick = 0
    private var structureDump: String? = null
    /** 探针是否**曾经**认定"这一页像课表"：决定终态文案走哪一支。 */
    private var probeSawSchedule = false
    private var scanStats: String? = null
    /** 自动进入课表的进展（点开了什么 / 没找到），只用于给用户看。 */
    private var entryClickHint: String? = null
    /** 终态标志：为真时 UI 必须给明确下一步，不允许继续"正在读取"。 */
    private var stalled = false
    /* 成功载荷的抽取统计与逐格证据（真机第六轮：只解析出 1 门课，其余格子静默丢失）。
     * 只服务于核对视图的展示与取证复制，不参与任何流程判定。 */
    private var readStats: String? = null
    private var cellEvidence: String? = null

    init {
        viewModelScope.launch {
            machine.state.collect { state ->
                // 核对计划在此捕获：toUiState() 是顶层纯函数，拿不到 ViewModel 实例状态，
                // 因此副作用（记住 pendingPlan）留在收集处，映射函数保持纯净。
                if (state is ImportState.Preview) pendingPlan = state.plan
                _uiState.value = state.toUiState()
                    .carryLoginDiagnostics()
                    .carryPreviewDiagnostics(readStats, cellEvidence)
            }
        }
    }

    /** 用户点了「开始登录」：启动流程（会挂起在 fetch，等真人登录后注入载荷）。 */
    fun startLogin() {
        _uiState.value = ImportUiState.LoggingIn()
        viewModelScope.launch { machine.start() }
    }

    /**
     * WebView 抽取完成，把载荷（schema JSON 文本）注入数据源。
     * 这是「等待登录」-> 「解析」的唯一入口。
     */
    fun onPayloadExtracted(payloadJson: String) {
        if (payloadJson.isBlank()) return
        // 先留一份诊断（读到几格 / 丢弃几格 / 逐格证据），再交数据源。
        // 顺序不能反：载荷一旦提交，解析结果里就不再有原始格子信息了。
        val (stats, evidence) = describeCellEvidence(payloadJson)
        readStats = stats
        cellEvidence = evidence
        viewModelScope.launch { webViewSource.submit(payloadJson) }
    }

    /* ------------------------------------------------------------------ *
     * WebView 登录现场：诊断上报与一次性指令
     * ------------------------------------------------------------------ */

    /** 页面地址变化（含 SPA pushState）。值未变则不重发，避免无意义重组。 */
    fun onPageUrlChanged(url: String) {
        if (url == currentUrl) return
        currentUrl = url
        reemitLoggingIn()
    }

    /** 每轮轻量探针的结论 JSON，翻译成一句人话给用户看。 */
    fun onProbeResult(probeJson: String) {
        val hint = describeProbe(probeJson)
        val saw = probeLooksLikeSchedule(probeJson)
        // 曾经命中过就一直记着：终态文案要靠它区分"找到表但解析不出"与"压根没找到表"
        if (hint == probeHint && saw == probeSawSchedule) return
        probeHint = hint
        probeSawSchedule = saw
        reemitLoggingIn()
    }

    /** 探测到唯一「我的课表」入口。歧义（0 个或多个候选）时脚本返回空串，这里也就无事发生。 */
    fun onScheduleEntryFound(entryJson: String) {
        val href = readEntryHref(entryJson) ?: return
        if (href == entryHref) return
        entryHref = href
        reemitLoggingIn()
    }

    /** 自动点击脚本每轮的结论 JSON -> 一句人话。值未变不重发，避免无意义重组。 */
    fun onEntryClicked(stepJson: String) {
        val hint = describeEntryClick(stepJson)
        if (hint == entryClickHint) return
        entryClickHint = hint
        reemitLoggingIn()
    }

    /** 用户点「读取课表」：自增计数即触发 WebView 跑一次完整抽取。 */
    fun requestManualExtract() {
        extractTick++
        reemitLoggingIn()
    }

    /** 用户点「进入课表」：把入口地址交给 WebView 加载。 */
    fun openScheduleEntry(href: String) {
        if (href.isBlank() || href == pendingUrl) return
        pendingUrl = href
        reemitLoggingIn()
    }

    /** 入口地址已交给 WebView：清空这条一次性指令，避免按钮卡在已触发态。 */
    fun onScheduleEntryLoaded() {
        if (pendingUrl == null) return
        pendingUrl = null
        reemitLoggingIn()
    }

    /** 用户点「复制页面结构」：自增即触发 WebView 导出课表结构。 */
    fun requestStructureDump() {
        dumpTick++
        reemitLoggingIn()
    }

    /** 结构导出结果：交给 UI 写剪贴板（剪贴板需要 Context，那是 UI 的事）。 */
    fun onStructureDumped(dumpJson: String) {
        structureDump = dumpJson
        reemitLoggingIn()
    }

    /** 已写入剪贴板：立刻清掉，避免同一次导出被复制两遍。 */
    fun onStructureDumpConsumed() {
        if (structureDump == null) return
        structureDump = null
        reemitLoggingIn()
    }

    /**
     * 认出了课表但**一条课都没解析出来**。
     *
     * 这是本轮真机"永远卡在正在读取"的直接成因：脚本既不产载荷也不报错。
     * 这里不提交空课表（下游拿到空课表更难排查），而是立刻进入终态并给出取证入口。
     */
    fun onExtractFailure(failureJson: String) {
        val stats = describeExtractFailure(failureJson)
        if (stats == scanStats && stalled) return
        scanStats = stats
        stalled = true
        reemitLoggingIn()
    }

    /** 轮询预算耗尽且从未注入过载荷：同样进终态，不允许无限"正在读取"。 */
    fun onPollExhausted() {
        if (stalled) return
        stalled = true
        reemitLoggingIn()
    }

    /** 核对视图确认导入：走数据层的单事务提交。 */
    fun confirmImport() {
        val plan = pendingPlan
        if (plan == null) {
            // 没有待确认计划却点了确认：显式失败，不静默回 Idle
            _uiState.value = ImportUiState.Failed(
                error = AppError.Parse(ParseStage.TABLE_STRUCTURE, "没有待确认的课表，请重新登录抓取"),
                retryable = true
            )
            return
        }
        viewModelScope.launch { machine.confirm(plan, payloadRef = null) }
    }

    /**
     * 返回修改 / 失败重试：回到 Idle。
     *
     * 注意 WebView 数据源的通道是一次性的（`Channel.BUFFERED`，`fetch()` 只收一次），
     * 因此同一次进入导入页只支持一轮抓取；重试需要重新进入本页（重建数据源）。
     * 这是当前 MVP 的已知限制，登记为待办而不是用"看起来能重试"的假按钮掩盖。
     */
    fun restart() {
        pendingPlan = null
        currentUrl = null
        probeHint = null
        entryHref = null
        extractTick = 0
        pendingUrl = null
        dumpTick = 0
        structureDump = null
        probeSawSchedule = false
        scanStats = null
        entryClickHint = null
        stalled = false
        readStats = null
        cellEvidence = null
        machine.reset()
        _uiState.value = ImportUiState.Idle
    }

    /**
     * 登录态重发时把诊断现场带过去。
     *
     * 不做这一步的后果：状态机 `AwaitingLogin -> Fetching` 的切换会发出一个全新的
     * `LoggingIn`（全 null），把刚抓到的当前 URL 与探针结论抹掉 —— 属于最贵的那类
     * 失效：不报错，只是悄悄少了一块信息。
     */
    private fun reemitLoggingIn() {
        val current = _uiState.value as? ImportUiState.LoggingIn ?: return
        _uiState.value = current.copy(
            currentUrl = currentUrl,
            probeHint = probeHint,
            entryHref = entryHref,
            extractTick = extractTick,
            pendingUrl = pendingUrl,
            dumpTick = dumpTick,
            structureDump = structureDump,
            probeSawSchedule = probeSawSchedule,
            scanStats = scanStats,
            entryClickHint = entryClickHint,
            stalled = stalled
        )
    }

    private fun ImportUiState.carryLoginDiagnostics(): ImportUiState {
        val logging = this as? ImportUiState.LoggingIn ?: return this
        return logging.copy(
            currentUrl = currentUrl,
            probeHint = probeHint,
            entryHref = entryHref,
            extractTick = extractTick,
            pendingUrl = pendingUrl,
            dumpTick = dumpTick,
            structureDump = structureDump,
            probeSawSchedule = probeSawSchedule,
            scanStats = scanStats,
            entryClickHint = entryClickHint,
            stalled = stalled
        )
    }
}
