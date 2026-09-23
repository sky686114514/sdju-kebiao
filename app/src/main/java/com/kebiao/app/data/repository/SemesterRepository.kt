package com.kebiao.app.data.repository

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.data.local.dao.ImportBatchDao
import com.kebiao.app.data.local.dao.SemesterDao
import com.kebiao.app.data.local.entity.ImportBatchEntity
import com.kebiao.app.data.repository.mapper.SemesterMapper
import com.kebiao.app.domain.model.ImportBatchRecord
import com.kebiao.app.domain.model.ImportBatchStatus
import com.kebiao.app.domain.model.ImportSourceKind
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.schedule.SemesterResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 学期仓储 —— **`is_current` 的唯一写入口**（Spec 第 5.2 节）。
 *
 * 不变式：任意时刻至多一行 `is_current = 1`（AC-23）。
 * 它由三件事共同保证，缺一不可：
 *  1. `SemesterDao.switchCurrent` / `upsertWithCurrentPolicy` 的事务性；
 *  2. 本类之外无人可以写 `is_current`（其它仓储只读）；
 *  3. 每次写操作后做一次不变式自检，异常时修复并记 `error_occurred` 事件（不静默）。
 *
 * 数据源唯一：`is_current` 只存在 DB 列里，不在 DataStore 另存一份，
 * 避免"双真相源"导致换学期后新旧数据混合（ADR-001）。
 */
class SemesterRepository(
    private val semesterDao: SemesterDao,
    private val importBatchDao: ImportBatchDao,
    private val eventLog: LocalEventLog,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    fun observeAll(): Flow<List<Semester>> =
        semesterDao.observeAll().map { rows -> rows.map(SemesterMapper::toDomain) }

    fun observeCurrent(): Flow<Semester?> =
        semesterDao.observeCurrent().map { row -> row?.let(SemesterMapper::toDomain) }

    fun observeRecentBatches(semesterId: Long, limit: Int = 20): Flow<List<ImportBatchRecord>> =
        importBatchDao.observeBySemester(semesterId, limit).map { rows -> rows.map(::toRecord) }

    suspend fun listAll(): AppResult<List<Semester>> =
        guard { semesterDao.listAll().map(SemesterMapper::toDomain) }

    suspend fun currentOnce(): AppResult<Semester?> =
        guard { semesterDao.listAll().firstOrNull { it.isCurrent }?.let(SemesterMapper::toDomain) }

    /**
     * 新增或更新一个学期。
     *
     * 先按 `(academic_year, term_index)` 查既有行以**保留主键 id**：
     * 直接用 `REPLACE` 会顺 `ON DELETE CASCADE` 把已有课程删光（见 [SemesterDao] 注释）。
     */
    suspend fun upsert(semester: Semester): AppResult<Long> = guard {
        val now = nowMillis()
        val existing = semesterDao.findByYearAndTerm(semester.academicYear, semester.termIndex)
        val createdAt = existing?.createdAt ?: now
        val entity = SemesterMapper.toEntity(semester, now = now, createdAt = createdAt)
        val id = semesterDao.upsertWithCurrentPolicy(entity, existingId = existing?.id, now = now)
        verifyInvariant("upsert")
        id
    }

    /** 切换当前学期：整个 App 的刷新链路由此触发（Spec 第 5.3 节）。 */
    suspend fun switchTo(id: Long): AppResult<Unit> = guard {
        val target = semesterDao.findById(id)
            ?: throw NoSuchElementException("学期 id=$id 不存在，无法切换")
        val now = nowMillis()
        semesterDao.switchCurrent(target.id, now)
        verifyInvariant("switchTo")
        eventLog.append(
            LocalEvent(
                name = LocalEvents.TERM_SWITCH,
                timestampMillis = now,
                termId = target.id,
                attributes = mapOf("academicYear" to target.academicYear, "termIndex" to target.termIndex.toString()),
            )
        )
    }

    suspend fun delete(id: Long): AppResult<Unit> = guard {
        semesterDao.deleteById(id)
        verifyInvariant("delete")
    }

    /**
     * 学期集合发生变化后推断一次"当前学期"（ARCHITECTURE.md 第 6.6 节）。
     *
     * 刻意**不在每次启动时调用**：用户在假期手动切到某个学期后，
     * 自动推断会把他的选择改回去。
     *
     * @return 推断结果；全库为空时为 null
     */
    suspend fun applyInferredCurrent(today: LocalDate): AppResult<Semester?> = guard {
        val rows = semesterDao.listAll().map(SemesterMapper::toDomain)
        if (rows.isEmpty()) return@guard null

        val currentCount = semesterDao.countCurrent()
        if (currentCount == 1) return@guard rows.firstOrNull { it.isCurrent }

        val resolved = SemesterResolver.infer(rows, today) ?: return@guard null
        semesterDao.switchCurrent(resolved.id, nowMillis())
        verifyInvariant("applyInferredCurrent")
        resolved.copy(isCurrent = true)
    }

    /**
     * 写操作后的不变式自检。
     *
     * 修复动作会记 `error_occurred` 事件而不是静默处理 —— 出现多个 current 说明
     * 有代码路径绕过了本仓储，必须留下痕迹供排查（AC-23 的"出现即不合格"要能被发现）。
     */
    private suspend fun verifyInvariant(operation: String) {
        val count = semesterDao.countCurrent()
        if (count > 1) {
            val repaired = semesterDao.repairMultipleCurrent(nowMillis())
            eventLog.append(
                LocalEvent(
                    name = LocalEvents.ERROR_OCCURRED,
                    timestampMillis = nowMillis(),
                    attributes = mapOf(
                        "kind" to "semester_current_invariant_violated",
                        "operation" to operation,
                        "currentCount" to count.toString(),
                        "repairedRows" to repaired.toString(),
                    ),
                )
            )
        }
    }

    private inline fun <T> guard(block: () -> T): AppResult<T> =
        try {
            AppResult.success(block())
        } catch (e: Exception) {
            AppResult.Failure(AppError.Storage(e).also { logFailure(it, e) })
        }

    private fun logFailure(error: AppError, cause: Throwable) {
        eventLog.append(
            LocalEvent(
                name = LocalEvents.ERROR_OCCURRED,
                timestampMillis = nowMillis(),
                attributes = mapOf(
                    "kind" to "semester_repository",
                    "message" to error.userMessage,
                    "cause" to (cause::class.simpleName ?: "unknown"),
                ),
            )
        )
    }

    private fun toRecord(entity: ImportBatchEntity): ImportBatchRecord = ImportBatchRecord(
        id = entity.id,
        semesterId = entity.semesterId,
        source = ImportSourceKind.fromCode(entity.source),
        status = ImportBatchStatus.fromCode(entity.status),
        payloadRef = entity.payloadRef,
        courseCount = entity.courseCount,
        sessionCount = entity.sessionCount,
        message = entity.message,
        startedAt = entity.startedAt,
        finishedAt = entity.finishedAt,
    )
}
