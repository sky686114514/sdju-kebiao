package com.kebiao.app.data.repository

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.LocalEvent
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.LocalEvents
import com.kebiao.app.core.ParseStage
import com.kebiao.app.data.import.parser.WeekExpressionParser
import com.kebiao.app.data.import.parser.WeekParseOutcome
import com.kebiao.app.data.local.dao.ClassSessionDao
import com.kebiao.app.data.local.dao.CourseDao
import com.kebiao.app.data.local.entity.ClassSessionEntity
import com.kebiao.app.data.local.entity.CourseEntity
import com.kebiao.app.data.repository.mapper.ClassSessionMapper
import com.kebiao.app.domain.model.ManualSessionDraft
import com.kebiao.app.domain.model.ManualSessionTarget
import com.kebiao.app.domain.model.SessionSource

/**
 * 手动编辑仓储（P1 兜底，AC-40 / AC-41）。
 *
 * 与导入的数据在 DB 层天然并存：`class_sessions.source` 区分来源，
 * 重新导入时只清 IMPORT 来源的行（见 `ClassSessionDao.deleteImportedBySemester`），
 * 因此手编项不会被导入覆盖。
 *
 * 本类里所有"手编不能碰导入数据"的约束都在**数据层强制**，不依赖 UI 自觉。
 */
class ManualSessionRepository(
    private val classSessionDao: ClassSessionDao,
    private val courseDao: CourseDao,
    private val parser: WeekExpressionParser,
    private val eventLog: LocalEventLog,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    /** 新增一门手动课程（不含课次；课次由 [saveSession] 单独写入）。 */
    suspend fun createCourse(
        semesterId: Long,
        name: String,
        code: String?,
        isOnline: Boolean,
    ): AppResult<Long> = guard {
        require(name.isNotBlank()) { "课程名不能为空" }
        val now = nowMillis()
        courseDao.insert(
            CourseEntity(
                semesterId = semesterId,
                name = name.trim(),
                code = code?.trim()?.takeIf { it.isNotEmpty() },
                totalHours = null,
                isOnline = isOnline,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    /**
     * 新增或修改一条课次。
     *
     * @param totalWeeks 学期总周数，用于周次串的上溢夹逼（与导入路径共用同一套语义）
     */
    suspend fun saveSession(
        target: ManualSessionTarget,
        draft: ManualSessionDraft,
        totalWeeks: Int,
    ): AppResult<Long> = guard {
        validate(draft)

        val weekSet = when (val outcome = parser.tryParse(draft.weeksRaw, totalWeeks)) {
            is WeekParseOutcome.Success -> outcome.weekSet
            is WeekParseOutcome.Failure ->
                throw HandledError(AppError.Parse(ParseStage.WEEK_EXPRESSION, outcome.error.message))
        }

        val now = nowMillis()
        when (target) {
            is ManualSessionTarget.New -> {
                val entity = ClassSessionEntity(
                    courseId = target.courseId,
                    weekday = draft.weekday?.value,
                    periodStart = draft.periodStart,
                    periodEnd = draft.periodEnd,
                    startTime = draft.startTime,
                    endTime = draft.endTime,
                    campus = draft.campus?.trim()?.takeIf { it.isNotEmpty() },
                    room = draft.room?.trim()?.takeIf { it.isNotEmpty() },
                    teacher = draft.teacher?.trim()?.takeIf { it.isNotEmpty() },
                    weeksRaw = draft.weeksRaw.trim(),
                    weekNumbers = weekSet.normalized(),
                    source = SessionSource.MANUAL.code,
                    importBatchId = null,
                    remark = draft.remark,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = classSessionDao.insert(entity)
                eventLog.append(
                    LocalEvent(
                        name = LocalEvents.MANUAL_COURSE_ADDED,
                        timestampMillis = now,
                        attributes = mapOf("sessionId" to id.toString(), "weeks" to weekSet.display()),
                    )
                )
                id
            }

            is ManualSessionTarget.Existing -> {
                val existing = classSessionDao.findById(target.sessionId)
                    ?: throw HandledError(AppError.NotFound("要修改的课次（id=${target.sessionId}）"))
                if (existing.source != SessionSource.MANUAL.code) {
                    throw HandledError(
                        AppError.Storage(
                            IllegalStateException(
                                "课次 id=${target.sessionId} 来自导入（source=IMPORT），不接受手动修改；" +
                                    "请在导入数据上修正后重新导入，或另存为手动课程。"
                            )
                        )
                    )
                }
                val updated = existing.copy(
                    weekday = draft.weekday?.value,
                    periodStart = draft.periodStart,
                    periodEnd = draft.periodEnd,
                    startTime = draft.startTime,
                    endTime = draft.endTime,
                    campus = draft.campus?.trim()?.takeIf { it.isNotEmpty() },
                    room = draft.room?.trim()?.takeIf { it.isNotEmpty() },
                    teacher = draft.teacher?.trim()?.takeIf { it.isNotEmpty() },
                    weeksRaw = draft.weeksRaw.trim(),
                    weekNumbers = weekSet.normalized(),
                    remark = draft.remark,
                    updatedAt = now,
                )
                classSessionDao.update(updated)
                updated.id
            }
        }
    }

    /** 删除一条手动课次；同样拒绝删除导入来源的行。 */
    suspend fun deleteSession(sessionId: Long): AppResult<Unit> = guard {
        val existing = classSessionDao.findById(sessionId)
            ?: throw HandledError(AppError.NotFound("要删除的课次（id=$sessionId）"))
        if (existing.source != SessionSource.MANUAL.code) {
            throw HandledError(
                AppError.Storage(
                    IllegalStateException(
                        "课次 id=$sessionId 来自导入，不接受手动删除；如需清理请重新导入或删除整个学期。"
                    )
                )
            )
        }
        classSessionDao.deleteById(sessionId)
    }

    /** 读取某课程下的全部课次（供编辑对话框预填与核对视图使用）。 */
    suspend fun sessionsOfCourse(courseId: Long): AppResult<List<com.kebiao.app.domain.model.ClassSession>> =
        guard { classSessionDao.listByCourse(courseId).map(ClassSessionMapper::toDomain) }

    private fun validate(draft: ManualSessionDraft) {
        val ps = draft.periodStart
        val pe = draft.periodEnd
        if (ps != null && pe != null && ps > pe) {
            throw HandledError(AppError.Parse(ParseStage.FIELD_MISSING, "起始节次不能大于结束节次"))
        }
        val st = draft.startTime
        val et = draft.endTime
        if (st != null && et != null && !st.isBefore(et)) {
            throw HandledError(AppError.Parse(ParseStage.FIELD_MISSING, "开始时间必须早于结束时间"))
        }
        if (ps != null && ps < 1) {
            throw HandledError(AppError.Parse(ParseStage.FIELD_MISSING, "节次从 1 开始"))
        }
    }

    /** 已分类的错误，直接透传给上层；不要被 [guard] 再包一层 `Unknown`。 */
    private class HandledError(val error: AppError) : Exception(error.userMessage)

    private inline fun <T> guard(block: () -> T): AppResult<T> =
        try {
            AppResult.success(block())
        } catch (e: HandledError) {
            eventLog.append(
                LocalEvent(
                    name = LocalEvents.ERROR_OCCURRED,
                    timestampMillis = nowMillis(),
                    attributes = mapOf("kind" to "manual_session", "message" to e.error.userMessage),
                )
            )
            AppResult.Failure(e.error)
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.Parse(ParseStage.FIELD_MISSING, e.message ?: "输入不合法"))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Storage(e))
        }
}
