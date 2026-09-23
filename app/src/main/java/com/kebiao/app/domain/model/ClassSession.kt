package com.kebiao.app.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * L3 上课安排 —— 落库的核心实体。
 *
 * 一条 [ClassSession] = 一组"周次集合 + 星期 + 节次 + 地点 + 教师"。
 *
 * 建模铁律（Spec 第 2.1 节）：形状 1（不同周换教室/教师）与形状 2（每周实验室不同）
 * **不能按课程表行简单存一行**，必须拆成周次互斥的多条 [ClassSession]。
 * "第 2 周 E教305 / 第 3-16 周 B105"只有这样才能同时满足"当周取到正确教室"与"周视图正确"。
 *
 * [weekday] 可为 null（如"军事技能"无固定星期）、[startTime] 可为 null。
 */
data class ClassSession(
    val id: Long,
    val courseId: Long,
    /** 1=周一 .. 7=周日（与 `java.time.DayOfWeek.value` 一致）；null = 无固定星期。 */
    val weekday: DayOfWeek?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val campus: String?,
    val room: String?,
    val teacher: String?,
    /** 原始周次串，保留以便审计回溯（如 `2-16 双周`）。 */
    val weeksRaw: String,
    val weekNumbers: WeekSet,
    val source: SessionSource,
    val importBatchId: Long?,
    val remark: String? = null,
) {
    /** 该课次是否属于"线上 / 不排座"分区（AC-51）：无固定星期即无法进网格。 */
    val isSchedulable: Boolean get() = weekday != null

    companion object {
        /**
         * 同一门课内两条 [ClassSession] 是否周次互斥。
         * 用于导入核对视图提示"同一门课出现周次重叠行"（通常是教务页面理解偏差的信号）。
         */
        fun weeksOverlap(a: ClassSession, b: ClassSession): Boolean =
            a.weekNumbers.weeks.any { it in b.weekNumbers.weeks }
    }
}
