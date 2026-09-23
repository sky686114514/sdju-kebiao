package com.kebiao.app.data.repository.mapper

import com.kebiao.app.data.local.entity.CourseEntity
import com.kebiao.app.domain.model.Course

/** `courses` 行 <-> 领域模型。 */
object CourseMapper {

    fun toDomain(entity: CourseEntity): Course = Course(
        id = entity.id,
        semesterId = entity.semesterId,
        name = entity.name,
        code = entity.code,
        totalHours = entity.totalHours,
        isOnline = entity.isOnline,
    )

    fun toEntity(domain: Course, now: Long, createdAt: Long = now): CourseEntity = CourseEntity(
        id = domain.id,
        semesterId = domain.semesterId,
        name = domain.name,
        code = domain.code,
        totalHours = domain.totalHours,
        isOnline = domain.isOnline,
        createdAt = createdAt,
        updatedAt = now,
    )
}
