package com.kebiao.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

/**
 * `class_sessions` 表 —— 本项目的核心表（Spec 第 6 节逐字实现）。
 *
 * 一条 = 一组"周次集合 + 星期 + 节次 + 地点 + 教师"。同一门课存在**周次互斥**的多条，
 * 这是唯一能表达"第 2 周在 E教305、第 3-16 周在 B105"的建模方式（Spec 第 2.1 节建模铁律）。
 *
 * [weekNumbers] 落库形态 `",2,4,6,8,"`（前后各一个分隔符，避免 LIKE 命中"12"这类子串）。
 * `weekday` 可为 NULL（"军事技能"无固定星期）；`source` 0=IMPORT / 1=MANUAL。
 */
@Entity(
    tableName = "class_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["import_batch_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["course_id"], name = "idx_sessions_course"),
        Index(value = ["weekday"], name = "idx_sessions_weekday"),
        // 外键子列必须建索引：import_batch_id 无索引时，父行（import_batches）的
        // 删除/更新会全表扫描 class_sessions（Room 对此有显式编译期警告）。
        // 这与 course_id 同为 FK 且已索引，两者必须一致 —— 属漏配修正，非过早优化
        // （Spec §6 不建索引针对的是查询列 week_numbers，不是 FK 子列）。
        Index(value = ["import_batch_id"], name = "idx_sessions_batch"),
    ],
)
data class ClassSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "course_id")
    val courseId: Long,

    /** 1=周一 .. 7=周日；NULL = 无固定星期。 */
    @ColumnInfo(name = "weekday")
    val weekday: Int?,

    @ColumnInfo(name = "period_start")
    val periodStart: Int?,

    @ColumnInfo(name = "period_end")
    val periodEnd: Int?,

    /** 落库为 TEXT `"08:10"`（见 `Converters`）。 */
    @ColumnInfo(name = "start_time")
    val startTime: LocalTime?,

    @ColumnInfo(name = "end_time")
    val endTime: LocalTime?,

    @ColumnInfo(name = "campus")
    val campus: String?,

    /** `"E教305"` / `"204(实验室2)"` / null（线上或无地点）。 */
    @ColumnInfo(name = "room")
    val room: String?,

    @ColumnInfo(name = "teacher")
    val teacher: String?,

    /** 原始周次串，审计回溯用（如 `2-16 双周`）。 */
    @ColumnInfo(name = "weeks_raw")
    val weeksRaw: String,

    /** 规范化周次集合，`",2,4,6,8,"`。 */
    @ColumnInfo(name = "week_numbers")
    val weekNumbers: String,

    /** 0 = IMPORT / 1 = MANUAL。重新导入时 MANUAL 项不得被覆盖（AC-41）。 */
    @ColumnInfo(name = "source")
    val source: Int,

    @ColumnInfo(name = "import_batch_id")
    val importBatchId: Long?,

    @ColumnInfo(name = "remark")
    val remark: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
