package com.kebiao.app.domain.model

/**
 * 手动新增 / 编辑一条 [ClassSession] 时由 UI 提交的输入。
 *
 * 对应 P1 兜底能力（AC-40 / AC-41）。它刻意只承载"可编辑字段"，
 * 不含 id / source / importBatchId —— 那些由仓储决定，UI 无法把它们改错。
 */
data class ManualSessionDraft(
    val weekday: java.time.DayOfWeek?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: java.time.LocalTime?,
    val endTime: java.time.LocalTime?,
    val campus: String?,
    val room: String?,
    val teacher: String?,
    /** 原始周次串，由 `WeekExpressionParser` 解析；解析失败必须报错而不是静默丢弃。 */
    val weeksRaw: String,
    val remark: String? = null,
)
