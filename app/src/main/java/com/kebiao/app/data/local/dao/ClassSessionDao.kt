package com.kebiao.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kebiao.app.data.local.entity.ClassSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassSessionDao {

    @Query(
        """
        SELECT s.* FROM class_sessions s
        INNER JOIN courses c ON s.course_id = c.id
        WHERE c.semester_id = :semesterId
        """
    )
    fun observeBySemester(semesterId: Long): Flow<List<ClassSessionEntity>>

    @Query(
        """
        SELECT s.* FROM class_sessions s
        INNER JOIN courses c ON s.course_id = c.id
        WHERE c.semester_id = :semesterId
        """
    )
    suspend fun listBySemester(semesterId: Long): List<ClassSessionEntity>

    /** 判断"这个学期是否有任何课次"，用于区分"尚未导入"与"今天没课"。 */
    @Query(
        """
        SELECT COUNT(*) FROM class_sessions s
        INNER JOIN courses c ON s.course_id = c.id
        WHERE c.semester_id = :semesterId
        """
    )
    fun observeCountBySemester(semesterId: Long): Flow<Int>

    @Query("SELECT * FROM class_sessions WHERE course_id = :courseId ORDER BY weekday, period_start")
    suspend fun listByCourse(courseId: Long): List<ClassSessionEntity>

    @Query("SELECT * FROM class_sessions WHERE id = :id")
    suspend fun findById(id: Long): ClassSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ClassSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ClassSessionEntity>): List<Long>

    @Update
    suspend fun update(entity: ClassSessionEntity)

    @Query("DELETE FROM class_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 重复导入前的清理第 1 步：只删 IMPORT 来源的课次。
     * MANUAL 来源（P1 手动编辑）刻意保留 —— 手编是兜底，不能被导入覆盖（AC-41）。
     */
    @Query(
        """
        DELETE FROM class_sessions
        WHERE source = 0
          AND course_id IN (SELECT id FROM courses WHERE semester_id = :semesterId)
        """
    )
    suspend fun deleteImportedBySemester(semesterId: Long)
}
