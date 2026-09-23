package com.kebiao.app.data.repository.mapper

import com.kebiao.app.core.DataIntegrityException
import com.kebiao.app.data.local.entity.ClassSessionEntity
import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.SessionSource
import com.kebiao.app.domain.model.WeekSet
import java.time.DayOfWeek

/** `class_sessions` 行 <-> 领域模型。 */
object ClassSessionMapper {

    fun toDomain(entity: ClassSessionEntity): ClassSession = ClassSession(
        id = entity.id,
        courseId = entity.courseId,
        weekday = entity.weekday?.let { code ->
            DayOfWeek.entries.firstOrNull { it.value == code }
                ?: throw DataIntegrityException("class_sessions.weekday 越界: $code（只允许 1..7 或 NULL）")
        },
        periodStart = entity.periodStart,
        periodEnd = entity.periodEnd,
        startTime = entity.startTime,
        endTime = entity.endTime,
        campus = entity.campus,
        room = entity.room,
        teacher = entity.teacher,
        weeksRaw = entity.weeksRaw,
        weekNumbers = parseWeeks(entity),
        source = SessionSource.entries.firstOrNull { it.code == entity.source }
            ?: throw DataIntegrityException("class_sessions.source 取值非法: ${entity.source}（只允许 0 / 1）"),
        importBatchId = entity.importBatchId,
        remark = entity.remark,
    )

    fun toEntity(domain: ClassSession, now: Long, createdAt: Long = now): ClassSessionEntity =
        ClassSessionEntity(
            id = domain.id,
            courseId = domain.courseId,
            weekday = domain.weekday?.value,
            periodStart = domain.periodStart,
            periodEnd = domain.periodEnd,
            startTime = domain.startTime,
            endTime = domain.endTime,
            campus = domain.campus,
            room = domain.room,
            teacher = domain.teacher,
            weeksRaw = domain.weeksRaw,
            weekNumbers = domain.weekNumbers.normalized(),
            source = domain.source.code,
            importBatchId = domain.importBatchId,
            remark = domain.remark,
            createdAt = createdAt,
            updatedAt = now,
        )

    private fun parseWeeks(entity: ClassSessionEntity): WeekSet =
        try {
            WeekSet.parseNormalized(entity.weekNumbers)
        } catch (e: IllegalArgumentException) {
            throw DataIntegrityException(
                "class_sessions.id=${entity.id} 的 week_numbers 已损坏: '${entity.weekNumbers}'" +
                    "（weeks_raw='${entity.weeksRaw}'）。原始串保留在现场，可据此重建。",
                e,
            )
        }
}
