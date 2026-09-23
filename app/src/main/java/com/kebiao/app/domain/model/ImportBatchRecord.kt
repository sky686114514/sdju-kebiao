package com.kebiao.app.domain.model

/**
 * 导入批次的只读审计记录（领域模型，供导入核对视图与设置页的日志入口展示）。
 *
 * 刻意与 Room 实体分离：UI 层不得接触 Entity（ARCHITECTURE.md 第 3.1 节）。
 */
data class ImportBatchRecord(
    val id: Long,
    val semesterId: Long,
    val source: ImportSourceKind,
    val status: ImportBatchStatus,
    /** 原始 HTML / JSON 的本地文件路径；不外传。 */
    val payloadRef: String?,
    val courseCount: Int?,
    val sessionCount: Int?,
    val message: String?,
    val startedAt: Long,
    val finishedAt: Long?,
)
