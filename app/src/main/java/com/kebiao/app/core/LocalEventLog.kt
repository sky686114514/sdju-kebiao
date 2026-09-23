package com.kebiao.app.core

/**
 * 本地事件日志（PRD 第 12 节：自用项目不接第三方埋点，改为本地轻量事件日志用于自检）。
 *
 * 事件名遵循 `{对象}_{动作}`。**不上报任何身份信息**：不记 IP、不记学号、不记密码，
 * 不记录原始教务页面内容。
 */
interface LocalEventLog {

    fun append(event: LocalEvent)

    /** 读取最近 [limit] 条，供设置页的"本地事件日志"入口展示。 */
    fun recent(limit: Int): List<LocalEvent>
}

/**
 * 一条本地事件。
 *
 * @param name 事件名（取自 [LocalEvents]）
 * @param termId 当前学期 id；无关联时为 null
 * @param attributes 附加属性，**调用方必须保证不含个人身份信息**
 */
data class LocalEvent(
    val name: String,
    val timestampMillis: Long,
    val termId: Long? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/** 事件名常量表（PRD 第 12 节逐条落地）。 */
object LocalEvents {
    const val IMPORT_START = "import_start"
    const val IMPORT_SUCCESS = "import_success"
    const val IMPORT_FAIL = "import_fail"

    /** 关键事件：记录解析结果与预期的差异，供人工核对。 */
    const val IMPORT_VERIFY_DIFF = "import_verify_diff"

    const val FIRST_VIEW_TODAY = "first_view_today"
    const val TODAY_VIEW = "today_view"
    const val WEEK_VIEW_OPEN = "week_view_open"
    const val WIDGET_REFRESH = "widget_refresh"

    const val TERM_SWITCH = "term_switch"
    const val TODAY_REFRESH_OK = "today_refresh_ok"

    const val REMIND_SCHEDULED = "remind_scheduled"
    const val REMIND_DELIVERED = "remind_delivered"
    const val REMIND_MISSED = "remind_missed"

    const val MANUAL_COURSE_ADDED = "manual_course_added"
    const val ERROR_OCCURRED = "error_occurred"
}
