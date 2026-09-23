package com.kebiao.app.di

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.kebiao.app.core.FileLocalEventLog
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.time.SystemTimeProvider
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.import.FilePayloadStore
import com.kebiao.app.data.import.ImportCommitter
import com.kebiao.app.data.import.ImportMapper
import com.kebiao.app.data.import.JsonFileImportSource
import com.kebiao.app.data.import.PayloadStore
import com.kebiao.app.data.import.WebViewImportSource
import com.kebiao.app.data.import.parser.ScheduleHtmlParser
import com.kebiao.app.data.import.parser.WeekExpressionParser
import com.kebiao.app.data.import.session.CookieStore
import com.kebiao.app.data.local.KebiaoDatabase
import com.kebiao.app.data.repository.ManualSessionRepository
import com.kebiao.app.data.repository.ScheduleRepository
import com.kebiao.app.data.repository.SemesterRepository
import com.kebiao.app.data.settings.ReminderSettingsRepository
import com.kebiao.app.reminder.ReminderScheduler
import com.kebiao.app.widget.AccentPaletteProvider
import com.kebiao.app.widget.EmptyAccentPaletteProvider
import com.kebiao.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 手写依赖容器（ADR-008：不引 Hilt；其 Gradle 插件会加深 AGP/Gradle 版本耦合）。
 *
 * 入口只装配：`KebiaoApplication` 拿到本容器后调一次 [bootstrap]，其余全部由这里提供。
 *
 * 全部依赖 `by lazy`：进程启动时不建库、不起闹钟，冷启动到"今日课程"的链路上
 * 只有 UI 需要的那几个对象被实例化（PRD 第 11 节：冷启动 < 2s）。
 */
class AppContainer private constructor(private val appContext: Context) {

    /** 应用级协程作用域：承载启动自检、广播触发的重排等"不依赖任何界面"的工作。 */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeProvider: TimeProvider by lazy { SystemTimeProvider() }

    val localEventLog: LocalEventLog by lazy { FileLocalEventLog(appContext) }

    private val database: KebiaoDatabase by lazy { KebiaoDatabase.create(appContext) }

    val semesterRepository: SemesterRepository by lazy {
        SemesterRepository(
            semesterDao = database.semesterDao(),
            importBatchDao = database.importBatchDao(),
            eventLog = localEventLog,
        )
    }

    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(
            classSessionDao = database.classSessionDao(),
            courseDao = database.courseDao(),
            semesterRepository = semesterRepository,
            timeProvider = timeProvider,
            eventLog = localEventLog,
        )
    }

    val manualSessionRepository: ManualSessionRepository by lazy {
        ManualSessionRepository(
            classSessionDao = database.classSessionDao(),
            courseDao = database.courseDao(),
            parser = WeekExpressionParser,
            eventLog = localEventLog,
        )
    }

    val reminderSettingsRepository: ReminderSettingsRepository by lazy {
        ReminderSettingsRepository(appContext)
    }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(
            context = appContext,
            scheduleRepository = scheduleRepository,
            settings = reminderSettingsRepository,
            timeProvider = timeProvider,
            eventLog = localEventLog,
        )
    }

    val widgetUpdater: WidgetUpdater by lazy {
        WidgetUpdater(context = appContext, eventLog = localEventLog)
    }

    val importMapper: ImportMapper by lazy { ImportMapper(WeekExpressionParser) }

    val scheduleHtmlParser: ScheduleHtmlParser by lazy { ScheduleHtmlParser() }

    val payloadStore: PayloadStore by lazy { FilePayloadStore(appContext) }

    val cookieStore: CookieStore by lazy {
        CookieStore(
            CookieManager.getInstance()
                ?: throw IllegalStateException("系统未提供 CookieManager，无法观察 WebView 会话")
        )
    }

    val importCommitter: ImportCommitter by lazy {
        ImportCommitter(
            database = database,
            courseDao = database.courseDao(),
            classSessionDao = database.classSessionDao(),
            importBatchDao = database.importBatchDao(),
            semesterRepository = semesterRepository,
            eventLog = localEventLog,
        )
    }

    /**
     * 课程识别色板 —— 由主题层实现（`ui/theme`）在启动时注入。
     *
     * 默认空实现：小组件不画识别环，但功能完整。这不是静默降级 ——
     * [AppBootstrap] 会在为空时写一条 `error_occurred`，使"颜色规则没接上"可被发现（D-1）。
     */
    var accentPaletteProvider: AccentPaletteProvider = EmptyAccentPaletteProvider

    /** 每次导入新建一个 WebView 数据源（会话是"一次性"的，不能跨导入复用）。 */
    fun newWebViewImportSource(): WebViewImportSource =
        WebViewImportSource(payloadStore = payloadStore)

    /** 每次导入新建一个 JSON 文件数据源。 */
    fun newJsonFileImportSource(uri: Uri): JsonFileImportSource =
        JsonFileImportSource(
            contentResolver = appContext.contentResolver,
            uri = uri,
            payloadStore = payloadStore,
        )

    /** 启动装配（幂等）。真正的启动副作用在 [AppBootstrap]。 */
    fun bootstrap() {
        if (!bootstrapped.compareAndSet(false, true)) return
        AppBootstrap(container = this, context = appContext).run(applicationScope)
    }

    private val bootstrapped = AtomicBoolean(false)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        /**
         * 取进程级单例。
         *
         * 广播接收器可能在 Application.onCreate 之后任意时刻被拉起，因此这里必须自建
         * （而不是依赖"Application 里存了一份静态引用"）。
         */
        fun from(context: Context): AppContainer {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: AppContainer(app).also { instance = it }
            }
        }
    }
}
