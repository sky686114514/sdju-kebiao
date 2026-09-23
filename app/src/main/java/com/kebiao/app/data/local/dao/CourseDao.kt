package com.kebiao.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kebiao.app.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE semester_id = :semesterId ORDER BY name")
    fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semester_id = :semesterId ORDER BY name")
    suspend fun listBySemester(semesterId: Long): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun findById(id: Long): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<CourseEntity>): List<Long>

    @Update
    suspend fun update(entity: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 重复导入前的清理第 2 步：删除该学期中**已经没有任何课次**的课程。
     *
     * 配合 [ClassSessionDao.deleteImportedBySemester] 使用：
     *  - 第 1 步删掉 IMPORT 来源的课次 -> 原导入课程变成"空壳课程"，由本语句清除；
     *  - 只含 MANUAL 课次的课程因仍有课次而保留（AC-41：手编项不被导入覆盖）。
     */
    @Query(
        """
        DELETE FROM courses
        WHERE semester_id = :semesterId
          AND id NOT IN (SELECT DISTINCT course_id FROM class_sessions)
        """
    )
    suspend fun deleteOrphansOfSemester(semesterId: Long)
}
