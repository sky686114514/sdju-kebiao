package com.kebiao.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kebiao.app.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

/**
 * `semesters` 表访问。
 *
 * **不使用 `OnConflictStrategy.REPLACE`**：SQLite 的 REPLACE 会先 DELETE 再 INSERT，
 * 顺着 `ON DELETE CASCADE` 把该学期的 courses / class_sessions / import_batches 一起删掉。
 * 唯一索引冲突由仓储层的「先查后 update/insert」处理。
 *
 * `is_current` 的写方法刻意只暴露"清空"与"标记"两个原语，由仓储把它们放进一个
 * `@Transaction` 里，从而保证"至多一个 current"的不变式（AC-23）。
 */
@Dao
interface SemesterDao {

    @Query("SELECT * FROM semesters ORDER BY academic_year DESC, term_index DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE is_current = 1 LIMIT 1")
    fun observeCurrent(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters ORDER BY academic_year DESC, term_index DESC")
    suspend fun listAll(): List<SemesterEntity>

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun findById(id: Long): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE academic_year = :academicYear AND term_index = :termIndex")
    suspend fun findByYearAndTerm(academicYear: String, termIndex: Int): SemesterEntity?

    /** 不变式自检用：任何时刻都必须 <= 1。 */
    @Query("SELECT COUNT(*) FROM semesters WHERE is_current = 1")
    suspend fun countCurrent(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SemesterEntity): Long

    @Update
    suspend fun update(entity: SemesterEntity)

    @Query("UPDATE semesters SET is_current = 0, updated_at = :now WHERE is_current = 1 AND id <> :keepId")
    suspend fun clearCurrentExcept(keepId: Long, now: Long)

    @Query("UPDATE semesters SET is_current = 0, updated_at = :now WHERE is_current = 1")
    suspend fun clearCurrent(now: Long)

    @Query("UPDATE semesters SET is_current = 1, updated_at = :now WHERE id = :id")
    suspend fun markCurrent(id: Long, now: Long)

    @Query("DELETE FROM semesters WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 唯一写入口的事务体：先把除目标外的 current 清掉，再把目标标为 current。
     * 两步在同一事务内，中途崩溃不会留下"两个 current"或"零个 current"。
     */
    @Transaction
    suspend fun switchCurrent(id: Long, now: Long) {
        clearCurrentExcept(keepId = id, now = now)
        markCurrent(id = id, now = now)
    }

    /**
     * 写入/更新学期，并就地维持"至多一个 current"不变式。
     *
     * 放在 DAO 的 `@Transaction` 默认方法里，而不是仓储层用 `db.withTransaction`：
     * 这样事务边界与 SQL 在一起，仓储层不需要持有 `RoomDatabase` 引用
     * （与 ARCHITECTURE.md 第 13 节的容器签名一致）。
     *
     * @param existingId 仓储层先查出的既有行 id；null 表示新插入
     * @return 该学期的主键 id
     */
    @Transaction
    suspend fun upsertWithCurrentPolicy(entity: SemesterEntity, existingId: Long?, now: Long): Long {
        val id: Long = if (existingId == null) {
            insert(entity.copy(id = 0L))
        } else {
            update(entity.copy(id = existingId))
            existingId
        }
        when {
            entity.isCurrent -> switchCurrent(id, now)
            // 一个学期都没有被标为 current 时，把当前这条兜成 current，避免出现"零个 current"
            // 导致 UI 误判为"尚未导入课表"（AC-11 的空状态必须可信）。
            countCurrent() == 0 -> markCurrent(id, now)
            else -> Unit
        }
        return id
    }

    /**
     * 不变式修复：若因外部写入（如手工 sqlite3 改库）出现多个 current，
     * 保留 id 最小者并清掉其余。返回被修复的行数，0 表示本来就正常。
     */
    @Transaction
    suspend fun repairMultipleCurrent(now: Long): Int {
        val currents = listAll().filter { it.isCurrent }.sortedBy { it.id }
        if (currents.size <= 1) return 0
        clearCurrentExcept(keepId = currents.first().id, now = now)
        return currents.size - 1
    }
}
