package com.kebiao.app.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/* =========================================================================
 * 自动进课表的驱动循环（原 `WebViewLoginScreen` 里的 D 循环）
 *
 * 拆出来的唯一原因：`WebViewLoginScreen.kt` 装完三条抽取路径 + 轮询后已 299 行，
 * 贴着「单文件 ≤300 行」的上限活，再加任何东西都会爆。
 * ========================================================================= */

/** 首屏留时间：登录页 / 门户首页渲染 + 抽屉动画都要等。 */
private const val AUTO_NAV_FIRST_DELAY_MS = 2500L

/** 点一次等一帧再探一次：SPA 抽屉展开与异步渲染都需要时间。 */
private const val AUTO_NAV_INTERVAL_MS = 2500L

/** 整会话点击上限。宁可点不够让用户手动补一下，也不无限点。 */
private const val AUTO_NAV_MAX_CLICKS = 6

/**
 * 自动进课表：每轮最多点 [ImportEntryClick.SCRIPT] 选中的那一个可见元素。
 *
 * 停机条件四选一：点满上限 / 已交付载荷 / WebView 已释放 / [WebViewHandle.autoNavDone]
 * （点到课表入口，或探针已认定当前页像课表 —— 后者由 `probeThenExtract` 置位）。
 *
 * **登录页绝不开点**：地址还没到教务门户（[isPortalUrl]）就直接退出本轮，
 * 等 `navigationEpoch` 变化（登录成功必然改地址 -> markNavigation -> 本 effect 重启）再起。
 * 这样用户在 SSO 页停留多久，我们都不会去点登录页上的任何东西。
 */
@Composable
internal fun AutoNavEffect(
    handle: WebViewHandle,
    onStep: (stepJson: String) -> Unit
) {
    LaunchedEffect(handle.navigationEpoch.value) {
        delay(AUTO_NAV_FIRST_DELAY_MS)
        while (handle.autoNavClicks < AUTO_NAV_MAX_CLICKS &&
            !handle.delivered && !handle.released && !handle.autoNavDone
        ) {
            val view = handle.view ?: break
            if (!isPortalUrl(view.url)) break
            view.evaluateJavascript(ImportEntryClick.SCRIPT) { raw ->
                // 回调可能晚于 onRelease：那之后一律不上报，与 runExtraction 同纪律。
                if (handle.released) return@evaluateJavascript
                val json = decodeJsonString(raw) ?: return@evaluateJavascript
                if (entryClickStep(json) == "entry") handle.autoNavDone = true
                onStep(json)
            }
            handle.autoNavClicks++
            delay(AUTO_NAV_INTERVAL_MS)
        }
    }
}
