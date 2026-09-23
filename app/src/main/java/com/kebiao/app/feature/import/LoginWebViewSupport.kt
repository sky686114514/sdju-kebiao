package com.kebiao.app.feature.import

import android.webkit.WebView
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import org.json.JSONTokener

/* =========================================================================
 * `WebViewLoginScreen` 的支撑件（WebView 句柄 + 三个纯函数）
 *
 * 拆出来只为守住「单文件 ≤300 行」：主文件要装三条抽取路径、轮询、四条铁律的
 * 说明，实测 339 行。
 * ========================================================================= */

/**
 * WebView 引用 + 轮询共享标志。
 *
 * [view] / [delivered] / [released] 只在回调与协程里读写，因此用普通字段；
 * [navigationEpoch] 需要在组合中被观察（它是 `LaunchedEffect` 的键），才是 State。
 *
 * 为什么引用不能用 Snapshot State 承载：`AndroidView.factory` 运行在组合/布局阶段，
 * 在那里写 State 是明确禁止的写法。
 */
internal class WebViewHandle {
    var view: WebView? = null

    /** 已经成功回传过一次载荷：为真时轮询立即停止。 */
    @Volatile var delivered = false

    /** WebView 已释放：迟到的 evaluateJavascript 回调不再产生任何上报。 */
    @Volatile var released = false

    /** 上一个已上报的地址，用于 diff（同一地址重复回调不上报）。 */
    @Volatile var lastUrl: String? = null

    /**
     * 自动导航**已完成**：点到课表入口，或探针已认定"这一页像课表"。
     * 为真时自动点击循环立即停止 —— 否则脚本会在课表页的面包屑上再点一次
     * 「我的课表」，把已经打开课表的用户弹出去。
     */
    @Volatile var autoNavDone = false

    /** 自动导航已点击的轮次。上限之外不再点，绝不无限点击。 */
    @Volatile var autoNavClicks = 0

    val navigationEpoch = mutableStateOf(0)

    fun markNavigation() {
        navigationEpoch.value++
    }

    /** 记下地址并返回"它是不是新的"。 */
    fun rememberUrl(url: String): Boolean {
        val changed = url != lastUrl
        lastUrl = url
        return changed
    }
}

/**
 * 载荷里是否至少有一门课。
 *
 * 抽取脚本在"认出课表但一条都没解析出来"时也会返回 JSON（courses 为空 + diagnostics），
 * 这里据此分流：有课才提交给数据层，没课只走诊断，绝不把空课表当成功。
 * 解不出来时按"没有课"处理 —— 宁可走诊断路径，也不把可疑文本当载荷提交。
 */
internal fun hasCourses(payloadJson: String): Boolean = try {
    JSONObject(payloadJson).optJSONArray("courses")?.length()?.let { it > 0 } ?: false
} catch (_: Exception) {
    false
}

/** 探针判定"像课表"：有表格且出现星期表头。解析不出来一律按"不像"处理（不猜）。 */
internal fun looksLikeSchedule(probeJson: String): Boolean = try {
    val probe = JSONObject(probeJson)
    probe.optInt("tables", 0) > 0 && probe.optBoolean("weekdayHeader", false)
} catch (_: Exception) {
    false
}

/**
 * evaluateJavascript 的回调值是 JSON 编码的字符串（含转义与首尾引号），
 * 需要解码回脚本真正返回的文本（即 schema JSON）。
 * 解不出字符串时返回 null（脚本返回了 null / 非字符串 / 空串）。
 */
internal fun decodeJsonString(raw: String?): String? {
    if (raw == null || raw == "null") return null
    return try {
        JSONTokener(raw).nextValue() as? String
    } catch (_: Exception) {
        null
    }
}

/**
 * 自动点击脚本的结论 JSON -> `step` 取值（`entry` / `menu` / `expand` / `none`）。
 *
 * 与 [looksLikeSchedule] 同风格：解不出来返回空串而不是猜一个值 —— 空串会走到
 * describeEntryClick 的 else 分支，如实显示"结果无法解析"。
 */
internal fun entryClickStep(json: String): String = try {
    JSONObject(json).optString("step", "")
} catch (_: Exception) {
    ""
}

/**
 * 当前地址是否已经在**教务门户**（而不是 SSO 登录页）。
 *
 * 手写解析而不是用 `android.net.Uri`：Uri 在 JVM 单测里是返回默认值的空壳，
 * 用它 = 单测永远拿到 null = 断言假绿（和 `org.json` 同一个坑）。
 *
 * 判据刻意粗粒度（主机名含 `jwgl`）：门户的域名形态各校不一，宁可"认宽一点
 * 由脚本自己的密码框总闸兜底"，也不要因为判得太死而永远不开点。
 */
internal fun isPortalUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = url.substringAfter("://", "").substringBefore("/").substringBefore(":")
    return host.contains("jwgl", ignoreCase = true)
}
