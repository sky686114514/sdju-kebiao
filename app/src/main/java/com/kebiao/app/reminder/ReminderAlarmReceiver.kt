package com.kebiao.app.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kebiao.app.R
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 上课提醒到点触发（[AlarmManager] 的目标）。
 *
 * 三个动作：
 *  - 默认（提醒）：发通知，含**课程名 + 时间 + 地点**（AC-30）；
 *  - `ACTION_SNOOZE`：再推到 10 分钟后；
 *  - `ACTION_DISMISS`：什么都不做（用户已看到）。
 *
 * 通知内容直接取自 Intent extras（调度时已算好），因此本接收器不做数据库读，
 * 保证在 Doze 唤醒后的极短窗口内也能完成（`onReceive` 有 10 秒上限）。
 *
 * **不静默**：通知发不出去（权限被拒 / Intent 残缺）时写 `remind_missed` 事件，
 * 让"提醒没响"这件事在本地日志里可查（PRD 第 12 节）。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = AppContainer.from(context)
        container.applicationScope.launch(Dispatchers.IO) {
            try {
                handle(context, intent, container)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent, container: AppContainer) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, INVALID_SESSION_ID)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME)
        val timeText = intent.getStringExtra(EXTRA_TIME_TEXT).orEmpty()
        val locationText = intent.getStringExtra(EXTRA_LOCATION_TEXT).orEmpty()

        if (sessionId == INVALID_SESSION_ID || courseName.isNullOrBlank()) {
            logMissed(container, sessionId, "intent 缺少必要字段（essionId / courseName）")
            return
        }

        when (intent.action) {
            ACTION_SNOOZE -> {
                container.reminderScheduler.snooze(sessionId, courseName, timeText, locationText)
                logDelivered(container, sessionId, event = LocalEvents.REMIND_SCHEDULED, detail = "snooze")
                return
            }

            ACTION_DISMISS -> {
                logDelivered(container, sessionId, event = LocalEvents.REMIND_DELIVERED, detail = "dismissed")
                return
            }

            else -> Unit // 落到下面的常规提醒路径
        }

        if (!hasNotificationPermission(context)) {
            logMissed(container, sessionId, "未授予 POST_NOTIFICATIONS，通知无法展示")
            return
        }

        NotificationChannels.ensure(context)
        NotificationManagerCompat.from(context).notify(sessionId.hashCode(), buildNotification(context, intent, courseName, timeText, locationText))
        logDelivered(container, sessionId, event = LocalEvents.REMIND_DELIVERED, detail = "posted")
    }

    private fun buildNotification(
        context: Context,
        intent: Intent,
        courseName: String,
        timeText: String,
        locationText: String,
    ): android.app.Notification {
        val detail = listOf(timeText, locationText).filter { it.isNotBlank() }.joinToString(" · ")
        return NotificationCompat.Builder(context, NotificationChannels.CLASS_REMINDER)
            .setSmallIcon(R.drawable.kebiao_ic_notification)
            .setContentTitle(courseName)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, context.getString(R.string.kebiao_action_dismiss), actionPendingIntent(context, intent, ACTION_DISMISS))
            .addAction(0, context.getString(R.string.kebiao_action_snooze), actionPendingIntent(context, intent, ACTION_SNOOZE))
            .build()
    }

    private fun openAppPendingIntent(context: Context): PendingIntent? {
        // 用启动 Intent（由包名解析）而不是硬编码 MainActivity 类名，
        // 避免提醒模块与 UI 模块的 Activity 命名耦合。
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionPendingIntent(context: Context, source: Intent, action: String): PendingIntent {
        val copy = buildIntent(
            context = context,
            sessionId = source.getLongExtra(EXTRA_SESSION_ID, INVALID_SESSION_ID),
            dateIso = source.getStringExtra(EXTRA_DATE).orEmpty(),
            courseName = source.getStringExtra(EXTRA_COURSE_NAME).orEmpty(),
            timeText = source.getStringExtra(EXTRA_TIME_TEXT).orEmpty(),
            locationText = source.getStringExtra(EXTRA_LOCATION_TEXT).orEmpty(),
            isSnooze = source.getBooleanExtra(EXTRA_IS_SNOOZE, false),
        ).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode() + copy.getIntExtra(EXTRA_SESSION_ID, 0),
            copy,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun logDelivered(container: AppContainer, sessionId: Long, event: String, detail: String) {
        container.localEventLog.append(
            LocalEvent(
                name = event,
                timestampMillis = System.currentTimeMillis(),
                attributes = mapOf("sessionId" to sessionId.toString(), "detail" to detail),
            )
        )
    }

    private fun logMissed(container: AppContainer, sessionId: Long, reason: String) {
        container.localEventLog.append(
            LocalEvent(
                name = LocalEvents.REMIND_MISSED,
                timestampMillis = System.currentTimeMillis(),
                attributes = mapOf("sessionId" to sessionId.toString(), "reason" to reason),
            )
        )
    }

    companion object {
        const val ACTION_SNOOZE: String = "com.kebiao.app.reminder.SNOOZE"
        const val ACTION_DISMISS: String = "com.kebiao.app.reminder.DISMISS"

        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_DATE = "session_date"
        private const val EXTRA_COURSE_NAME = "course_name"
        private const val EXTRA_TIME_TEXT = "time_text"
        private const val EXTRA_LOCATION_TEXT = "location_text"
        private const val EXTRA_IS_SNOOZE = "is_snooze"

        private const val INVALID_SESSION_ID = -1L
        private const val OPEN_APP_REQUEST_CODE = 0x2100

        /** 通知动作类的请求码前缀（与 `ReminderScheduler` 的闹钟请求码空间分开）。 */
        private const val ACTION_REQUEST_CODE_PREFIX = 0x7F000000

        /**
         * 构造提醒 Intent。
         *
         * 用显式组件（[ReminderAlarmReceiver]）+ `setPackage` 限定投递目标，
         * 且接收器在清单里 `exported="false"`，因此 Intent 里的课程信息不会被第三方应用读到。
         */
        fun buildIntent(
            context: Context,
            sessionId: Long,
            dateIso: String,
            courseName: String,
            timeText: String,
            locationText: String,
            isSnooze: Boolean,
        ): Intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setPackage(context.packageName)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_DATE, dateIso)
            .putExtra(EXTRA_COURSE_NAME, courseName)
            .putExtra(EXTRA_TIME_TEXT, timeText)
            .putExtra(EXTRA_LOCATION_TEXT, locationText)
            .putExtra(EXTRA_IS_SNOOZE, isSnooze)
    }
}
