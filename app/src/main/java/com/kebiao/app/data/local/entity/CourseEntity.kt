package com.kebiao.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `courses` 表（Spec 第 6 节逐字实现）。
 *
 * L2 逻辑课程；"同一门课不同周换教室"的变体不在本表，而在 `class_sessions` 的互斥周次行上。
 */
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["semester_id"], name = "idx_courses_semester")],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "semester_id")
    val semesterId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    /** 教务未提供时为 null，不臆造。 */
    @ColumnInfo(name = "code")
    val code: String?,

    @ColumnInfo(name = "total_hours")
    val totalHours: Int?,

    @ColumnInfo(name = "is_online", defaultValue = "0")
    val isOnline: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
