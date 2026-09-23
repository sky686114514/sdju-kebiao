package com.kebiao.app.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.di.AppContainer

/**
 * 自愈入口三：精确闹钟权限状态变化后立即重排（AC-33）。
 *
 * 场景：用户在系统"闹钟与提醒"页里刚把权限打开（或刚关掉）。
 * 不处理的话，App 要等到下一次日更 Worker 才会重排 —— 期间提醒一直是降级或缺失状态。
 *
 * 该广播只在 API 31+ 存在，因此做版本判断；低版本无此广播，直接返回。
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val action = intent.action
        if (action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            log(context, "unexpected_action:${action ?: "null"}")
            return
        }

        ReminderWorker.enqueueOneShot(context)
    }

    private fun log(context: Context, detail: String) {
        AppContainer.from(context).localEventLog.append(
            LocalEvent(
                name = LocalEvents.ERROR_OCCURRED,
                timestampMillis = System.currentTimeMillis(),
                attributes = mapOf("kind" to "exact_alarm_permission_receiver", "detail" to detail),
            )
        )
    }
}
