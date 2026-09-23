package com.kebiao.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `import_batches` 表（Spec 第 6 节逐字实现）。
 *
 * 导入审计与回滚留痕的载体：即使整批回滚，本表仍保留一条 `status = ROLLED_BACK` 记录，
 * 使"导入失败"这件事本身可追溯（AC-06：已入库数据不得被破坏，且失败要可见）。
 *
 * `source` / `status` 在实体层就是 `Int`，语义枚举定义在领域层
 * （[com.kebiao.app.domain.model.ImportSourceKind] / [com.kebiao.app.domain.model.ImportBatchStatus]），
 * 避免领域模型反向依赖 Room。
 */
@Entity(
    tableName = "import_batches",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["semester_id"], name = "idx_batches_semester")],
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "semester_id")
    val semesterId: Long,

    /** 0 = WEBVIEW / 1 = JSON_FILE。 */
    @ColumnInfo(name = "source")
    val source: Int,

    /** 0 = RUNNING / 1 = SUCCESS / 2 = FAILED / 3 = ROLLED_BACK。 */
    @ColumnInfo(name = "status")
    val status: Int,

    /** 原始 HTML / JSON 的本地文件路径；不外传，可为 null。 */
    @ColumnInfo(name = "payload_ref")
    val payloadRef: String?,

    @ColumnInfo(name = "course_count")
    val courseCount: Int?,

    @ColumnInfo(name = "session_count")
    val sessionCount: Int?,

    @ColumnInfo(name = "message")
    val message: String?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
)
