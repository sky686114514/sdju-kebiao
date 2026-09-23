package com.kebiao.app.data.import.session

import android.webkit.CookieManager

/**
 * WebView 会话的只读观察器。
 *
 * ## 设计纪律（Spec 第 4.3 节强制约束 5/6）
 *
 *  1. **不手工拼接 `Cookie` 请求头** —— CAS 的 `route` / `JSESSIONID` / `CASTGC`
 *     由系统 `CookieManager` 与 WebView 共同管理，手工搬运是最容易出错的一环。
 *  2. **不把 Cookie 落盘**：本类只在内存里做"有没有会话"的判断，不写文件、不进日志值。
 *  3. 诊断只输出 Cookie 的**名字集合**（用于判断是不是登录失败），绝不输出值。
 *
 * 凭据持久化（"下次免登录"）是 OD-007 的待裁决项，本版本**不做**，
 * 因此本类没有任何加密存储代码，也不假装有。
 */
class CookieStore(private val cookieManager: CookieManager) {

    /** 指定 URL 下是否已存在会话 Cookie（用于判断 SSO 是否已登录成功）。 */
    fun hasSession(url: String): Boolean = cookiesOf(url).isNotEmpty()

    /**
     * 会话 Cookie 的**名字**集合（不含值），仅用于本地诊断。
     * 例如登录成功后通常能看到 `JSESSIONID` / `CASTGC` / `route`。
     */
    fun sessionCookieNames(url: String): Set<String> = cookiesOf(url)
        .mapNotNull { pair ->
            val name = pair.substringBefore('=').trim()
            name.takeIf { it.isNotEmpty() && !isTrackingName(name) }
        }
        .toSet()

    /** 清理会话：退出导入流程、或用户主动"清除登录状态"时调用。 */
    fun clear() {
        @Suppress("DEPRECATION")
        cookieManager.removeAllCookie()
        cookieManager.flush()
    }

    private fun cookiesOf(url: String): List<String> {
        val raw = cookieManager.getCookie(url) ?: return emptyList()
        return raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 排除 WebView 可能附带的分析类 Cookie，避免把无关项当作"已登录"的证据。 */
    private fun isTrackingName(name: String): Boolean =
        name.startsWith("_ga") || name.startsWith("_gid") || name.startsWith("Hm_")
}
