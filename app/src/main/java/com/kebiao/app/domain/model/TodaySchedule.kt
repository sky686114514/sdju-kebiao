package com.kebiao.app.domain.model

import java.time.LocalDate

/**
 * "今天"的完整求值结果 —— UI 只订阅它，从不自己算今天（ADR-001 / Spec 第 5.2 节）。
 *
 * 三种"没有课"在这里被显式建模为三个不同的 [TodayEmptyReason]，而不是一个笼统的"空"
 * —— Spec 第 7 节要求四种空状态文案必须可区分，且禁止写"暂无数据"。
 */
data class TodaySchedule(
    val date: LocalDate,
    val semester: Semester?,
    /** 学期内的第几周；学期外为 null。 */
    val weekOfSemester: Int?,
    val items: List<SessionOccurrence>,
    val emptyReason: TodayEmptyReason?,
) {
    init {
        require((items.isEmpty()) == (emptyReason != null)) {
            "items 与 emptyReason 必须互斥：items=${items.size}, reason=$emptyReason"
        }
    }

    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        fun notImported(date: LocalDate) = TodaySchedule(date, null, null, emptyList(), TodayEmptyReason.NOT_IMPORTED)

        fun outsideSemester(date: LocalDate, semester: Semester?) =
            TodaySchedule(date, semester, null, emptyList(), TodayEmptyReason.OUTSIDE_SEMESTER)

        fun noClassToday(
            date: LocalDate,
            semester: Semester,
            week: Int,
        ) = TodaySchedule(date, semester, week, emptyList(), TodayEmptyReason.NO_CLASS_TODAY)

        fun content(
            date: LocalDate,
            semester: Semester,
            week: Int,
            items: List<SessionOccurrence>,
        ) = TodaySchedule(date, semester, week, items, null)
    }
}

/** 今天没有课的三种可区分原因（第四种"有课"由 [TodaySchedule.items] 非空表达）。 */
enum class TodayEmptyReason {
    /** 尚未导入课表 —— UI 需给"去导入"引导。 */
    NOT_IMPORTED,

    /** 学期之外：假期 / 学期未开始 / 已结束。 */
    OUTSIDE_SEMESTER,

    /** 学期内但今天确实没课 —— UI 需给"下节课"提示。 */
    NO_CLASS_TODAY,
}
