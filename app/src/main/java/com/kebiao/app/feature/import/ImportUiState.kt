package com.kebiao.app.feature.import

import com.kebiao.app.core.AppError
import java.time.DayOfWeek
import java.time.LocalTime

/* =========================================================================
 * 导入流程 UiState（Spec §7 + UIUX §8 + ARCHITECTURE §7.6）
 *
 * 流程：登录（WebView 中继） -> 抓取 -> 核对视图 -> 单事务提交 -> 结果
 *
 * 硬约束（Spec §4.3）：
 *  - App 不读取密码字段、不注册 @JavascriptInterface
 *  - 解析失败必须显式报错，禁止静默返回空集（AC-04）
 *  - 导入后必须有核对视图（哪些课 / 哪些周 / 哪些教室）——
 *    对应竞品「导入识别错 -> 旷课」这一头号差评
 * ========================================================================= */

/** 核对视图的一行：一门课的抓取摘要。 */
data class ImportCourseSummary(
    val name: String,
    val sessionCount: Int,
    /** 例如 "3-16 周 · 周二 1-2 节 · B105 · 袁艳红"，用真实抓取数据拼装。 */
    val detailLines: List<String>
)

/** 解析告警（周次表达式无法识别等），必须展示而不是吞掉（AC-04）。 */
data class ImportWarning(val courseName: String, val raw: String, val reason: String)

/**
 * @param readStats 抽取统计的一句话人话（读到几格 / 入库几门 / 丢弃几格），
 *   来自成功载荷的 `diagnostics`；失败载荷没有 diagnostics 时为 null。
 * @param cellEvidence 逐格证据的 JSON 原文（供用户复制出来取证），同样只来自
 *   `diagnostics`。**只用于展示与复制，不参与任何流程判定**。
 */
data class ImportPreview(
    val courseCount: Int,
    val sessionCount: Int,
    val courses: List<ImportCourseSummary>,
    val warnings: List<ImportWarning>,
    val readStats: String? = null,
    val cellEvidence: String? = null
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

sealed interface ImportUiState {

    /** 尚未开始：显示登录说明与「开始登录」入口。 */
    data object Idle : ImportUiState

    /**
     * WebView 登录中（真人在系统 WebView 完成 SSO，含短信验证码）。
     *
     * 携带的字段**只用于诊断与引导，不参与流程判定**（真机首验 H1/H2/H3 的产物）：
     *  - [currentUrl]：当前页面地址。SPA 的 pushState 跳转不产生整页加载，
     *    靠 `doUpdateVisitedHistory` 捕获，这是判断"卡在门户首页还是进了课表"的唯一现场；
     *  - [probeHint]：轻量探针结论（这一页像不像课表），真机上用来定位"为什么没读到"；
     *  - [entryHref]：探测到的「我的课表」入口（候选唯一才给，歧义时为空）；
     *  - [extractTick]：用户点「立即读取」的一次性指令（自增即触发）；
     *  - [pendingUrl]：用户点「进入课表」后待 WebView 加载的地址，加载完即清空；
     *  - [dumpTick]：用户点「复制页面结构」的一次性指令（自增即触发）；
     *  - [structureDump]：结构导出结果，交给 UI 写入剪贴板后即清空；
     *  - [probeSawSchedule]：探针**曾经**认定"这一页像课表"；
     *  - [scanStats]：抽取的扫描计数（扫到几格 / 几格没识别出周次）；
     *  - [entryClickHint]：自动点击进展的一句话人话（点开了菜单 / 已点进课表 /
     *    没找到入口），只用于展示，不参与任何流程判定；
     *  - [stalled]：**终态标志** —— 轮询耗尽或抽取解析出 0 条，且从未注入过载荷。
     *    为真时 UI 必须显示明确的下一步，**不允许继续停在"正在读取"**。
     *
     * 注意：这些字段会频繁变化；[ImportScreen] 的 `AnimatedContent` 必须为登录态设固定
     * `contentKey`，否则每次诊断更新都会把 WebView 拆掉重建，SSO 会话直接丢失。
     */
    data class LoggingIn(
        val currentUrl: String? = null,
        val probeHint: String? = null,
        val entryHref: String? = null,
        val extractTick: Int = 0,
        val pendingUrl: String? = null,
        val dumpTick: Int = 0,
        val structureDump: String? = null,
        val probeSawSchedule: Boolean = false,
        val scanStats: String? = null,
        val entryClickHint: String? = null,
        val stalled: Boolean = false
    ) : ImportUiState

    /** 抓取 / 解析中。 */
    data class Fetching(val step: String, val progress: Float?) : ImportUiState

    /** 核对视图：提交前让用户看到抓到了什么。 */
    data class Verifying(val preview: ImportPreview) : ImportUiState

    /** 单事务提交中。 */
    data object Committing : ImportUiState

    data class Success(val courseCount: Int, val sessionCount: Int) : ImportUiState

    data class Failed(val error: AppError, val retryable: Boolean) : ImportUiState
}

/** 手动录入 / 编辑一条上课安排（P1 兜底，与导入课程并存不冲突）。 */
data class ManualSessionInput(
    val courseName: String,
    val weekday: DayOfWeek?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val room: String?,
    val teacher: String?,
    val weeksRaw: String
) {
    /** 缺课程名或周次串即不可提交；周次串的合法性由 WeekExpressionParser 判定。 */
    val isReadyToSubmit: Boolean
        get() = courseName.isNotBlank() && weeksRaw.isNotBlank()
}
