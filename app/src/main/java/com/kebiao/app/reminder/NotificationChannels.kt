package com.kebiao.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.kebiao.app.R

/**
 * 通知渠道管理。
 *
 * 幂等：重复调用安全（渠道已存在时 Android 会忽略创建）。
 * 渠道 id 一经发布不可更改（改了等于换渠道、用户设置会丢），因此用常量固定下来。
 */
object NotificationChannels {

    /** 上课提醒渠道：重要性 HIGH（要有提示音与横幅，否则"早八不迟到"落不了地）。 */
    const val CLASS_REMINDER: String = "class_reminder"

    /** 每日早课汇总渠道：重要性 DEFAULT，不打扰。 */
    const val DAILY_SUMMARY: String = "daily_summary"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: throw IllegalStateException("系统未提供 NotificationManager，无法创建通知渠道")

        val reminder = NotificationChannel(
            CLASS_REMINDER,
            context.getString(R.string.kebiao_channel_class_reminder_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.kebiao_channel_class_reminder_desc)
            enableVibration(true)
        }

        val summary = NotificationChannel(
            DAILY_SUMMARY,
            context.getString(R.string.kebiao_channel_daily_summary_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.kebiao_channel_daily_summary_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(reminder, summary))
    }
}
