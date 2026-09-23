package com.kebiao.app.ui.components

import com.kebiao.app.domain.model.SessionOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* =========================================================================
 * 课刻 · 展示文案格式化（纯函数，无副作用，可单测）
 *
 * 与 domain 的 SemesterFormatting 分工：domain 负责周次计算，UI 负责中文展示串。
 * 本文件只做「数据 -> 字符串」，不访问系统时间。
 * ========================================================================= */

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private val WEEKDAY_ZH = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** DayOfWeek -> 「周一」…「周日」。 */
fun weekdayZh(day: DayOfWeek): String = WEEKDAY_ZH[day.value - 1]

/** LocalTime -> "08:10"。 */
fun formatTime(time: LocalTime?): String = time?.format(TIME_FMT) ?: ""

/** LocalDate -> "9月20日"。 */
fun formatDate(date: LocalDate): String = date.format(DATE_FMT)

/** 时间区间："08:10-09:40"。任一端缺失返回空串。 */
fun formatTimeRange(start: LocalTime?, end: LocalTime?): String {
    if (start == null || end == null) return ""
    return "${formatTime(start)}-${formatTime(end)}"
}

/** 节次："1-2 节" / "1 节" / ""（无节次）。 */
fun formatPeriods(periodStart: Int?, periodEnd: Int?): String {
    val s = periodStart ?: return ""
    val e = periodEnd ?: return "$s 节"
    return if (s == e) "$s 节" else "$s-$e 节"
}

/** 卡片中行的完整时间串："08:10-09:40 · 1-2 节"；无时间时返回 "时间待定"。 */
fun formatTimeLine(occurrence: SessionOccurrence): String {
    val range = formatTimeRange(occurrence.startTime, occurrence.endTime)
    val periods = formatPeriods(occurrence.periodStart, occurrence.periodEnd)
    return when {
        range.isEmpty() && periods.isEmpty() -> "时间待定"
        range.isEmpty() -> periods
        periods.isEmpty() -> range
        else -> "$range · $periods"
    }
}

/**
 * 地点文案。
 *  - 纯线上课：返回 null 由调用方渲染 wifi 图标 + ONLINE_LABEL
 *  - 无固定地点：「以学院通知为准」
 */
const val ONLINE_LABEL: String = "线上"
const val TBD_LOCATION_LABEL: String = "以学院通知为准"
const val TBD_TIME_LABEL: String = "时间待定"

fun formatLocation(occurrence: SessionOccurrence): String? = when {
    occurrence.isOnline -> null
    !occurrence.room.isNullOrBlank() -> occurrence.room
    else -> null
}

/** 地点 + 教师 合并行："B105 · 袁艳红"。二者皆无返回 null。 */
fun formatPlaceAndTeacher(occurrence: SessionOccurrence): String? {
    val parts = buildList {
        formatLocation(occurrence)?.let { add(it) }
        occurrence.teacher?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/**
 * 空状态的「下节课」行：`下节课：周四 08:10 大学物理B(1) · B105 · 袁艳红`
 * 用真实数据算出。线上课的地点段显示「线上」。
 */
fun formatNextClassLine(occurrence: SessionOccurrence): String {
    val head = "${weekdayZh(occurrence.weekday)} ${formatTime(occurrence.startTime)}"
    val mid = occurrence.courseName
    val tail = buildList {
        val place = formatLocation(occurrence)
        if (place != null) {
            add(place)
        } else if (occurrence.isOnline) {
            add(ONLINE_LABEL)
        }
        occurrence.teacher?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" · ")
    return if (tail.isEmpty()) "$head $mid" else "$head $mid · $tail"
}

/** 页面副标题："9月20日 周日"。 */
fun formatTodaySubtitle(date: LocalDate): String = "${formatDate(date)} ${weekdayZh(date.dayOfWeek)}"

/**
 * 学期名的紧凑形式，用于 TopAppBar 右侧 Chip（空间有限）。
 * "2026-2027 学年第 1 学期" -> "2026-2027-1"；无法识别的名字原样返回。
 */
fun shortSemesterName(name: String): String {
    val compact = name
        .replace(Regex("""\s*学年\s*"""), "-")
        .replace(Regex("""\s*第\s*"""), "")
        .replace("学期", "")
        .replace(Regex("""\s+"""), "-")
        .trim('-')
    return compact.ifBlank { name }
}

/** 周次标注：「第 1 教学周」。 */
fun formatWeekLabel(week: Int): String = "第 $week 教学周"

/** 周视图日期范围："2026-09-14 至 2026-09-20"。 */
fun formatDateRange(start: LocalDate, end: LocalDate): String =
    "${start.format(ISO_DATE)} 至 ${end.format(ISO_DATE)}"

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
