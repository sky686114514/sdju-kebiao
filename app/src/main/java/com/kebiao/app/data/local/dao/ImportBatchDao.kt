package com.kebiao.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kebiao.app.data.local.entity.ImportBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportBatchDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ImportBatchEntity): Long

    /**
     * 结束一个批次。`status` 由调用方给 [com.kebiao.app.data.local.entity.ImportBatchStatus] 的 code。
     *
     * 注意：**回滚后的留痕写在独立事务里**（不能写进被回滚的那个事务，否则记录会一起消失），
     * 因此本方法不接受事务包装，由 `ImportCommitter` 在提交失败后单独调用。
     */
    @Query(
        """
        UPDATE import_batches
        SET status = :status,
            course_count = :courseCount,
            session_count = :sessionCount,
            message = :message,
            finished_at = :finishedAt
        WHERE id = :id
        """
    )
    suspend fun finish(
        id: Long,
        status: Int,
        courseCount: Int?,
        sessionCount: Int?,
        message: String?,
        finishedAt: Long,
    )

    @Query("SELECT * FROM import_batches WHERE semester_id = :semesterId ORDER BY started_at DESC LIMIT :limit")
    fun observeBySemester(semesterId: Long, limit: Int = 20): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun findById(id: Long): ImportBatchEntity?

    @Query("SELECT * FROM import_batches ORDER BY started_at DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<ImportBatchEntity>
}
