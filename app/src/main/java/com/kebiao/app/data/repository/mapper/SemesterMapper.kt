package com.kebiao.app.data.repository.mapper

import com.kebiao.app.data.local.entity.SemesterEntity
import com.kebiao.app.domain.model.Semester

/**
 * `semesters` 行 <-> 领域模型。
 *
 * 映射只做形状转换，不含业务规则（code-organization 第 1 节：Repository/映射层禁止含业务逻辑）。
 */
object SemesterMapper {

    fun toDomain(entity: SemesterEntity): Semester = Semester(
        id = entity.id,
        name = entity.name,
        academicYear = entity.academicYear,
        termIndex = entity.termIndex,
        startDate = entity.startDate,
        totalWeeks = entity.totalWeeks,
        isCurrent = entity.isCurrent,
    )

    fun toEntity(domain: Semester, now: Long, createdAt: Long = now): SemesterEntity = SemesterEntity(
        id = domain.id,
        name = domain.name,
        academicYear = domain.academicYear,
        termIndex = domain.termIndex,
        startDate = domain.startDate,
        totalWeeks = domain.totalWeeks,
        isCurrent = domain.isCurrent,
        createdAt = createdAt,
        updatedAt = now,
    )
}
