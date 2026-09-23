package com.kebiao.app.feature.import

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/* =========================================================================
 * 教务系统登录屏（WebView 中继，Spec §4.3）
 *
 * 铁律（违反即退回）：
 *  1. **不注册 @JavascriptInterface** —— 抽取结果通过 evaluateJavascript 的
 *     回调取回，不建立任何 Java 对象给页面调用。
 *  2. **不读取密码字段** —— 本 WebView 只加载 SSO 页面，不查询、不注入、
 *     不缓存任何输入控件内容。
 *  3. allowFileAccess / allowContentAccess 全关；mixedContentMode NEVER_ALLOW
 *     （全链路 HTTPS）。
 *
 * ## 抽取时机（真机首验 2026-09-20 的校准：登录成功却停在门户首页）
 * 教务门户是**单页应用**：从首页点进「我的课表」走的是 pushState，不再产生整页加载。
 * 因此只在 `onPageFinished` 里抽取 = 永远只有第一次机会（H1）；课表卡片若异步渲染，
 * page-finished 时 DOM 还没填好，也不会再来一次回调（H2）。本屏因此有三条抽取路径：
 *
 *   A. `onPageFinished` —— 整页加载（首次进门户 / SSO 跳转）时立刻跑一次；
 *   B. `doUpdateVisitedHistory` —— **SPA pushState 的唯一可靠钩子**，站内跳转时
 *      上报 URL + 重置轮询预算 + 跑一次抽取（覆盖 H1）；
 *   C. 轮询兜底 —— 每 [PROBE_INTERVAL_MS] 先读回 `view.url` 做 diff（覆盖 hash 路由
 *      这类不更新访问历史的情况），再跑轻量探针 [ImportWebView.PROBE_SCRIPT]，
 *      **仅当探针认定"像课表"** 才执行重的 [extractionScript]（覆盖 H2）。
 *      预算 [PROBE_MAX_ROUNDS] 轮，且**每次导航重置**：用户在登录页停留多久都不消耗
 *      预算，一旦真的跳转（含 SPA）就重新给一整个窗口等异步渲染。
 *   D. 自动进课表（[AutoNavEffect]）—— 替用户点开菜单抽屉进课表；
 *      **地址还没到教务门户就绝不开点**（真机第四轮加固：不能去点登录页上的东西）。
 *
 * 抽取脚本由 [ImportWebView.EXTRACTION_SCRIPT] 提供（产出 schema JSON），
 * UI 只负责执行与回传。教务改版时真人自适应，代码只改脚本常量一处。
 *
 * 回调时效性：`AndroidView.factory` 只在首次组合时执行一次，WebViewClient 会一直
 * 持有第一次传入的回调。因此脚本与回调都必须经 [rememberUpdatedState] 取值，
 * 否则重组后 WebView 仍会调用**过期闭包**（表现为"第二个页面之后不再抽取"）。
 * ========================================================================= */

/** 轮询间隔。1.5s 是"能覆盖异步渲染"与"不空转烧 CPU"之间的折中。 */
private const val PROBE_INTERVAL_MS = 1500L

/** 单次导航后的轮询预算上限（40 轮 ≈ 60s）。到点即停，不做无限轮询。 */
private const val PROBE_MAX_ROUNDS = 40

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLoginScreen(
    startUrl: String,
    extractionScript: String,
    onPageFinished: (url: String) -> Unit,
    onExtract: (payloadJson: String) -> Unit,
    modifier: Modifier = Modifier,
    /** 页面地址变化（含 SPA pushState）：用于诊断"当前停在哪一页"。 */
    onPageUrlChanged: (url: String) -> Unit = {},
    /** 每轮探针的结论 JSON，只做展示与取证，不参与流程判定。 */
    onProbe: (probeJson: String) -> Unit = {},
    /** 探测到唯一「我的课表」入口时的 JSON（含 href / text）。 */
    onScheduleEntry: (entryJson: String) -> Unit = {},
    /** 自动进课表每一轮的结论 JSON（点了什么 / 没找到），只用于给用户看进展。 */
    onEntryClick: (stepJson: String) -> Unit = {},
    /** 自增即触发一次完整抽取（用户点「立即读取」，跳过探针门控）。 */
    extractTick: Int = 0,
    /** 自增即导出一次课表结构（用户点「复制页面结构」，用于真机取证）。 */
    dumpTick: Int = 0,
    /** 待加载的地址（用户点「进入课表」）；加载后立刻回调 [onPendingUrlLoaded]。 */
    pendingUrl: String? = null,
    onPendingUrlLoaded: () -> Unit = {},
    /**
     * 抽取**失败**（`{"ok":false,"reason":…}）：不入库，只回传诊断，
     * 让 UI 说清卡在哪一步，而不是永远停在"正在读取"。原因见 [ExtractFailureReason]。
     */
    onExtractFailure: (failureJson: String) -> Unit = {},
    /** 结构导出结果（仅课表那张表，不含 body / URL / 表单）。 */
    onStructureDumped: (dumpJson: String) -> Unit = {},
    /** 轮询预算耗尽且从未注入过载荷：UI 必须给终态，不允许继续"正在读取"。 */
    onPollExhausted: () -> Unit = {}
) {
    val latestScript by rememberUpdatedState(extractionScript)
    val latestPageFinished by rememberUpdatedState(onPageFinished)
    val latestExtract by rememberUpdatedState(onExtract)
    val latestUrlChanged by rememberUpdatedState(onPageUrlChanged)
    val latestProbe by rememberUpdatedState(onProbe)
    val latestEntry by rememberUpdatedState(onScheduleEntry)
    val latestEntryClick by rememberUpdatedState(onEntryClick)
    val latestPendingLoaded by rememberUpdatedState(onPendingUrlLoaded)
    val latestExtractFailure by rememberUpdatedState(onExtractFailure)
    val latestStructureDumped by rememberUpdatedState(onStructureDumped)
    val latestPollExhausted by rememberUpdatedState(onPollExhausted)

    // 轮询协程跑在 AndroidView 之外，需要一处稳定的 WebView 引用与共享标志。
    // 引用刻意用**普通字段**承载：factory 运行在组合/布局阶段，在那里写 Snapshot
    // State 是明确禁止的。唯一需要在组合里被观察的是 navigationEpoch（决定预算重置）。
    val handle = remember { WebViewHandle() }
    val navigationEpoch by handle.navigationEpoch

    // 抽取脚本执行结果通过 evaluateJavascript 的 JSON 回调返回，避免 JS 接口。
    // 已经交付过载荷或 WebView 已释放：再跑重脚本没有意义（数据层的通道是一次性的，
    // 多交一次也无人接收），统一在这里挡掉，三条抽取路径共用同一道闸门。
    val runExtraction = remember(handle) {
        extract@ { view: WebView ->
            if (handle.delivered || handle.released) return@extract
            view.evaluateJavascript(latestScript) { raw ->
                val payload = decodeJsonString(raw)
                if (payload.isNullOrBlank() || handle.released) return@evaluateJavascript
                if (hasCourses(payload)) {
                    // 成功回传过一次就停轮询：后续轮次没有意义，只会空跑重脚本
                    handle.delivered = true
                    latestExtract(payload)
                } else {
                    // 抽取失败（{"ok":false,"reason":…}）：不提交任何载荷，只回传诊断。
                    // 真机"卡在正在读取"的根因就是这一步原先静默返回空串。
                    latestExtractFailure(payload)
                }
            }
        }
    }

    // 探针 -> 命中才跑重脚本：无关页面（登录页、门户首页）上每轮只做一次
    // 轻量 DOM 统计，真正的抽取只在"像课表"时发生。
    val probeThenExtract = remember(handle) {
        { view: WebView ->
            view.evaluateJavascript(ImportWebView.PROBE_SCRIPT) { raw ->
                val json = decodeJsonString(raw)
                if (!json.isNullOrBlank()) latestProbe(json)
                if (!json.isNullOrBlank() && looksLikeSchedule(json)) {
                    // 已是课表页就绝不再自动点击：否则会在面包屑上再点一次「我的课表」把用户弹走。
                    handle.autoNavDone = true
                    runExtraction(view)
                }
            }
        }
    }

    // 入口探测：只**读取** href，绝不点击（宁可让用户自己点，也不能替他乱点）。
    val scanScheduleEntry = remember(handle) {
        { view: WebView ->
            view.evaluateJavascript(ImportWebView.SCHEDULE_ENTRY_SCRIPT) { raw ->
                val json = decodeJsonString(raw)
                if (!json.isNullOrBlank()) latestEntry(json)
            }
        }
    }

    // 地址变化统一处理：上报 -> 重置轮询预算 -> 探入口。
    // 用 handle 记住上一个地址，重复回调（SSO 跳转常连发两次）不会重复上报。
    val onUrlDetected = remember(handle) {
        { view: WebView, url: String ->
            if (handle.rememberUrl(url)) {
                latestUrlChanged(url)
                handle.markNavigation()
                scanScheduleEntry(view)
            }
        }
    }

    // 导航（整页加载 + SPA pushState）统一入口：在上述处理之后立刻试一次抽取
    val onNavigated = remember(handle) {
        { view: WebView, url: String ->
            onUrlDetected(view, url)
            runExtraction(view)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // 账号密码保护：Android O 起 `setSaveFormData` 已无效果 —— 表单数据改由
                    // 系统 Autofill 服务保存。因此改用平台层的**有效**开关，把 WebView 排除在
                    // 自动填充之外（minSdk 26，无需版本守卫）。
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                    settings.apply {
                        javaScriptEnabled = true          // SSO 登录页必需
                        domStorageEnabled = true
                        allowFileAccess = false          // 禁止本地文件访问
                        allowContentAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        // databaseEnabled（WebSQL）自 API 19 起废弃、且默认即 false：
                        // 不调用即为关闭，显式赋值只会多一条废弃告警。
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latestPageFinished(url)
                            onNavigated(view, url)
                        }

                        /**
                         * SPA pushState / replaceState 不触发 [onPageFinished]，
                         * 但一定会更新访问历史 —— 这是站内路由唯一可靠的钩子（H1）。
                         */
                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String,
                            isReload: Boolean
                        ) {
                            onNavigated(view, url)
                        }
                    }
                    handle.view = this
                    loadUrl(startUrl)
                }
            },
            onRelease = { view ->
                handle.released = true
                handle.view = null
                view.stopLoading()
                view.destroy()
            }
        )
    }

    // C. 轮询兜底（覆盖 H2）：预算按导航重置，命中即抽，成功即停。
    LaunchedEffect(navigationEpoch) {
        var rounds = 0
        while (rounds < PROBE_MAX_ROUNDS && !handle.delivered && !handle.released) {
            val view = handle.view
            if (view != null) {
                // hash 路由（`#/xxx`）不触发 doUpdateVisitedHistory，靠读回当前地址兜底；
                // 地址确实变了就走一次"导航"处理（含重置预算）。
                val url = view.url
                if (!url.isNullOrBlank()) onUrlDetected(view, url)
                probeThenExtract(view)
            }
            rounds++
            if (rounds >= PROBE_MAX_ROUNDS) break
            delay(PROBE_INTERVAL_MS)
        }
        // 预算耗尽仍一无所获：必须让 UI 收尾，绝不允许停在"正在读取"。
        if (rounds >= PROBE_MAX_ROUNDS && !handle.delivered && !handle.released) {
            latestPollExhausted()
        }
    }

    // D. 自动进课表：每轮最多点一次，登录页绝不开点（实现见 ImportAutoNav）。
    AutoNavEffect(handle = handle, onStep = latestEntryClick)

    // 「我已打开课表，立即读取」：跳过探针门控直接抽一次（探针可能过于保守）。
    LaunchedEffect(extractTick) {
        if (extractTick == 0) return@LaunchedEffect
        handle.view?.let(runExtraction)
    }

    // 「进入课表」：加载探测到的入口，随后立刻回报以清空指令（避免按钮卡在已触发态）。
    LaunchedEffect(pendingUrl) {
        val url = pendingUrl ?: return@LaunchedEffect
        handle.view?.loadUrl(url)
        latestPendingLoaded()
    }

    // 「复制页面结构」：导出课表那张表的结构（隐私纪律见 ImportStructureDump）。
    LaunchedEffect(dumpTick) {
        if (dumpTick == 0) return@LaunchedEffect
        val view = handle.view ?: return@LaunchedEffect
        view.evaluateJavascript(ImportWebView.STRUCTURE_DUMP_SCRIPT) { raw ->
            val json = decodeJsonString(raw)
            if (!json.isNullOrBlank()) latestStructureDumped(json)
        }
    }
}
