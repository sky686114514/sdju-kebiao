package com.kebiao.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.di.AppContainer
import com.kebiao.app.widget.WidgetUpdater

/**
 * 自愈入口一：系统事件触发的重排。
 *
 * 处理：
 *  - `BOOT_COMPLETED`：重启后 AlarmManager 里的闹钟全部丢失，必须重排；
 *  - `MY_PACKAGE_REPLACED`：应用更新会清掉已排闹钟；
 *  - `TIME_SET` / `TIMEZONE_CHANGED`：闹钟按绝对时刻（`RTC_WAKEUP`）排定，改时间/时区后必须重算，
 *    否则"提前 15 分钟"会变成提前几小时或已经过期 —— 这是 RTC 语义的必然结果，不是可选项；
 *  - `DATE_CHANGED`：跨零点，`todayFlow` 会重发，但小组件需要一次显式刷新。
 *
 * 其它 action 一律不处理，但**不静默**：写一条 `error_occurred` 以便排查清单配置错误。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null) {
            log(context, "system_event_without_action")
            return
        }

        val handled = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                ReminderWorker.enqueueOneShot(context)
                WidgetUpdater.enqueueRefresh(context)
                true
            }

            Intent.ACTION_DATE_CHANGED -> {
                WidgetUpdater.enqueueRefresh(context)
                true
            }

            else -> false
        }

        if (!handled) {
            log(context, "unhandled_action:$action")
        }
    }

    private fun log(context: Context, detail: String) {
        AppContainer.from(context).localEventLog.append(
            LocalEvent(
                name = LocalEvents.ERROR_OCCURRED,
                timestampMillis = System.currentTimeMillis(),
                attributes = mapOf("kind" to "boot_receiver", "detail" to detail),
            )
        )
    }
}
