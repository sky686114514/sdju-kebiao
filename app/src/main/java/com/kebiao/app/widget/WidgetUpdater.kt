package com.kebiao.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.di.AppContainer

/**
 * 小组件刷新器（ARCHITECTURE.md 第 9.2 节）。
 *
 * 刷新触发点：数据变更 / 学期切换 / 跨零点 / 周期兜底 / App 回到前台。
 * 调用方只需调 [refresh]（挂起）或 [enqueueRefresh]（不阻塞、走 WorkManager）。
 */
class WidgetUpdater(
    private val context: Context,
    private val eventLog: LocalEventLog,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * 立即刷新所有小组件实例。
     *
     * @return 失败时返回 [AppResult.Failure]，**不静默**：小组件不刷新是用户可见的缺陷，
     *         必须能通过本地事件日志回溯（PRD 第 12 节的 `widget_refresh`）。
     */
    suspend fun refresh(): AppResult<Unit> = try {
        KebiaoWidget().updateAll(context)
        eventLog.append(
            LocalEvent(name = LocalEvents.WIDGET_REFRESH, timestampMillis = nowMillis())
        )
        AppResult.success(Unit)
    } catch (e: Exception) {
        eventLog.append(
            LocalEvent(
                name = LocalEvents.ERROR_OCCURRED,
                timestampMillis = nowMillis(),
                attributes = mapOf(
                    "kind" to "widget_refresh_failed",
                    "cause" to (e::class.simpleName ?: "unknown"),
                ),
            )
        )
        AppResult.Failure(com.kebiao.app.core.AppError.Storage(e))
    }

    companion object {
        private const val UNIQUE_WORK = "kebiao_widget_refresh"

        /** 异步刷新（供广播接收器等不能阻塞的场景使用）。 */
        fun enqueueRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

/** 承载 [WidgetUpdater.enqueueRefresh] 的 Worker。 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.from(applicationContext)
        return when (container.widgetUpdater.refresh()) {
            is AppResult.Success -> Result.success()
            is AppResult.Failure -> Result.retry()
        }
    }
}
