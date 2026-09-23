package com.kebiao.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * `semesters` 表（Spec 第 6 节逐字实现）。
 *
 * 唯一索引 `(academic_year, term_index)` 防止同一学期被重复导入成两行。
 * 注意：**不要用 `OnConflictStrategy.REPLACE` 处理这个唯一冲突** —— SQLite 的 REPLACE
 * 是"先 DELETE 再 INSERT"，会顺着外键 `ON DELETE CASCADE` 把该学期的 courses /
 * class_sessions / import_batches 全部删掉（静默数据丢失）。仓储层改为
 * 「先查后 update/insert」，保留主键 id。
 */
@Entity(
    tableName = "semesters",
    indices = [
        Index(value = ["academic_year", "term_index"], unique = true, name = "idx_semesters_year_term"),
    ],
)
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "academic_year")
    val academicYear: String,

    /** 1 = 秋 / 2 = 春 / 3 = 短学期。 */
    @ColumnInfo(name = "term_index")
    val termIndex: Int,

    /** ISO-8601 `2026-09-14`；列类型 TEXT。 */
    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    @ColumnInfo(name = "total_weeks")
    val totalWeeks: Int,

    /** "至多一个当前学期" 不变式的载体，唯一写入口是 `SemesterRepository`。 */
    @ColumnInfo(name = "is_current", defaultValue = "0")
    val isCurrent: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
