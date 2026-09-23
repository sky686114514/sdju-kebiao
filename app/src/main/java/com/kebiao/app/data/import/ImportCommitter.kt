package com.kebiao.app.data.import

import androidx.room.withTransaction
import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.core.ParseStage
import com.kebiao.app.data.local.KebiaoDatabase
import com.kebiao.app.data.local.dao.ClassSessionDao
import com.kebiao.app.data.local.dao.CourseDao
import com.kebiao.app.data.local.dao.ImportBatchDao
import com.kebiao.app.data.local.entity.ClassSessionEntity
import com.kebiao.app.data.local.entity.CourseEntity
import com.kebiao.app.data.local.entity.ImportBatchEntity
import com.kebiao.app.data.repository.SemesterRepository
import com.kebiao.app.domain.model.ImportBatchStatus
import com.kebiao.app.domain.model.ImportSourceKind
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionSource

/**
 * 导入提交器 —— **单事务提交 + 失败整体回滚**（Spec 第 5.2 节 / ARCHITECTURE.md 第 7.6 节）。
 *
 * 写入顺序与事务边界（这一步的划分是本类最重要的设计决定）：
 *
 * ```
 * 1. 学期行  upsert                          <- 独立事务
 * 2. 批次行  insert(status = RUNNING)         <- 独立事务（审计记录的落脚点）
 * 3. 课表数据（删旧 IMPORT + 插新课程与课次）  <- 核心：单事务，失败整体回滚
 * 4. 批次行  finish(SUCCESS / ROLLED_BACK)    <- 独立事务
 * ```
 *
 * 为什么批次行必须留在数据事务之外？
 * 因为它就是"失败留痕"的载体：若把它放进第 3 步，回滚会连留痕一起抹掉，
 * 用户只会看到"点了导入、什么都没发生"。Spec 第 6 节要求回滚后 `status = ROLLED_BACK` 可见。
 *
 * 为什么学期行也要独立？
 * 因为 `import_batches.semester_id` 是 NOT NULL 外键，批次行必须先有学期才能插入。
 * 副作用是"导入失败会留下一个没有课表的空学期"—— 这不是半截脏数据，
 * 它在 UI 上就等价于"尚未导入课表"，语义正确（AC-06 要求的是已入库的课表数据不被破坏）。
 */
class ImportCommitter(
    private val database: KebiaoDatabase,
    private val courseDao: CourseDao,
    private val classSessionDao: ClassSessionDao,
    private val importBatchDao: ImportBatchDao,
    private val semesterRepository: SemesterRepository,
    private val eventLog: LocalEventLog,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun commit(
        plan: ImportPlan,
        source: ImportSourceKind,
        payloadRef: String?,
    ): AppResult<ImportResult> {
        val semesterId = when (val upserted = upsertSemester(plan)) {
            is AppResult.Success -> upserted.value
            is AppResult.Failure -> return upserted
        }

        val batchId = try {
            importBatchDao.insert(
                ImportBatchEntity(
                    semesterId = semesterId,
                    source = source.code,
                    status = ImportBatchStatus.RUNNING.code,
                    payloadRef = payloadRef,
                    courseCount = null,
                    sessionCount = null,
                    message = null,
                    startedAt = nowMillis(),
                    finishedAt = null,
                )
            )
        } catch (e: Exception) {
            return AppResult.Failure(AppError.Storage(e))
        }

        return try {
            val counts = database.withTransaction { replaceAndInsert(semesterId, batchId, plan) }
            importBatchDao.finish(
                id = batchId,
                status = ImportBatchStatus.SUCCESS.code,
                courseCount = counts.courses,
                sessionCount = counts.sessions,
                message = null,
                finishedAt = nowMillis(),
            )
            log(plan, semesterId, LocalEvents.IMPORT_SUCCESS, "courses=${counts.courses} sessions=${counts.sessions}")
            logIssues(plan, semesterId)
            AppResult.success(ImportResult(batchId, semesterId, counts.courses, counts.sessions, plan.issues))
        } catch (e: Exception) {
            // 数据已整体回滚；这里只补一条留痕，绝不吞掉错误。
            try {
                importBatchDao.finish(
                    id = batchId,
                    status = ImportBatchStatus.ROLLED_BACK.code,
                    courseCount = null,
                    sessionCount = null,
                    message = "写入失败，已整体回滚：${e.message ?: e::class.simpleName}",
                    finishedAt = nowMillis(),
                )
            } catch (auditError: Exception) {
                // 留痕本身失败：显式记录，不静默。此时原始失败原因 e 一定会在下面被返回。
                eventLog.append(
                    LocalEvent(
                        name = LocalEvents.ERROR_OCCURRED,
                        timestampMillis = nowMillis(),
                        termId = semesterId,
                        attributes = mapOf(
                            "kind" to "import_rollback_marker_failed",
                            "cause" to (auditError::class.simpleName ?: "unknown"),
                            "originalCause" to (e::class.simpleName ?: "unknown"),
                        ),
                    )
                )
            }
            log(plan, semesterId, LocalEvents.IMPORT_FAIL, e.message ?: e::class.simpleName ?: "unknown")
            AppResult.Failure(AppError.Storage(e))
        }
    }

    /**
     * 事务体：先删旧 IMPORT 数据，再插入新数据。
     *
     * 删除策略（AC-41）：只删 `source = 0`（IMPORT）的课次，
     * 再清理"已经没有任何课次"的课程 —— 只含 MANUAL 课次的课程会保留。
     */
    private suspend fun replaceAndInsert(
        semesterId: Long,
        batchId: Long,
        plan: ImportPlan,
    ): Counts {
        classSessionDao.deleteImportedBySemester(semesterId)
        courseDao.deleteOrphansOfSemester(semesterId)

        val now = nowMillis()
        var sessionTotal = 0
        for (planned in plan.courses) {
            val courseId = courseDao.insert(
                CourseEntity(
                    semesterId = semesterId,
                    name = planned.name,
                    code = planned.code,
                    totalHours = planned.totalHours,
                    isOnline = planned.isOnline,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            val rows = planned.sessions.map { session ->
                ClassSessionEntity(
                    courseId = courseId,
                    weekday = session.weekday?.value,
                    periodStart = session.periodStart,
                    periodEnd = session.periodEnd,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    campus = session.campus,
                    room = session.room,
                    teacher = session.teacher,
                    weeksRaw = session.weeksRaw,
                    weekNumbers = session.weekNumbers.normalized(),
                    source = SessionSource.IMPORT.code,
                    importBatchId = batchId,
                    remark = session.remark,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            if (rows.isNotEmpty()) {
                classSessionDao.insertAll(rows)
                sessionTotal += rows.size
            }
        }
        return Counts(courses = plan.courses.size, sessions = sessionTotal)
    }

    private suspend fun upsertSemester(plan: ImportPlan): AppResult<Long> {
        val startDate = plan.semester.startDate
            ?: return AppResult.Failure(AppError.Parse(ParseStage.FIELD_MISSING, "学期起始日缺失，无法写库"))
        return semesterRepository.upsert(
            Semester(
                id = 0L,
                name = plan.semester.name,
                academicYear = plan.semester.academicYear,
                termIndex = plan.semester.termIndex,
                startDate = startDate,
                totalWeeks = plan.totalWeeks,
                // 不自动切到新导入的学期：PRD 场景 D 要求"导入后由用户显式切换"，
                // 自动切换会让用户在学期列表里的选择被悄悄改掉。
                isCurrent = false,
            )
        )
    }

    private fun log(plan: ImportPlan, semesterId: Long, name: String, detail: String) {
        eventLog.append(
            LocalEvent(
                name = name,
                timestampMillis = nowMillis(),
                termId = semesterId,
                attributes = mapOf(
                    "semester" to plan.semester.name,
                    "detail" to detail,
                ),
            )
        )
    }

    /** `import_verify_diff`：把核对提示项写进本地日志，供设置页的日志入口回看。 */
    private fun logIssues(plan: ImportPlan, semesterId: Long) {
        if (plan.issues.isEmpty()) return
        plan.issues.forEach { issue ->
            eventLog.append(
                LocalEvent(
                    name = LocalEvents.IMPORT_VERIFY_DIFF,
                    timestampMillis = nowMillis(),
                    termId = semesterId,
                    attributes = mapOf(
                        "kind" to issue.kind.name,
                        "course" to (issue.courseName ?: "-"),
                        "raw" to (issue.raw ?: "-"),
                    ),
                )
            )
        }
    }

    private data class Counts(val courses: Int, val sessions: Int)
}
