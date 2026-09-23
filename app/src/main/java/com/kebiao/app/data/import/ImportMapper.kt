package com.kebiao.app.data.import

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.ParseStage
import com.kebiao.app.data.import.parser.WeekExpressionParser
import com.kebiao.app.data.import.parser.WeekParseOutcome
import com.kebiao.app.domain.model.WeekSet
import com.kebiao.app.domain.schedule.SemesterFormatting
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 把任意来源的 [RawSchedule] 映射为 [ImportPlan]。
 *
 * 三条数据入口汇入同一个 Mapper，因此**周次解析、字段校验、提示项收集只有一份实现**
 * （ARCHITECTURE.md 第 7.4 节）。
 *
 * 失败处理纪律（AC-04 / AC-07）：
 *  - 任何一条 `weeksRaw` 解析失败 -> **整批失败**，并给出"课程名 + 原始串"作为现场；
 *  - 绝不"跳过这一条继续"，也绝不静默返回空集。
 */
class ImportMapper(private val parser: WeekExpressionParser = WeekExpressionParser) {

    fun map(raw: RawSchedule): AppResult<ImportPlan> {
        val startDate = raw.semester.startDate
            ?: return fail(
                ParseStage.FIELD_MISSING,
                "学期起始日缺失，无法计算教学周（需要教务系统的开学日期或由用户校准）",
            )

        if (raw.courses.isEmpty()) {
            return fail(ParseStage.TABLE_STRUCTURE, "解析结果中没有任何课程，原始页面已保留用于排查")
        }

        val issues = mutableListOf<ImportIssue>()

        // ---------- 第 1 遍：只做语法解析、不做上界夹逼，以便推导学期体量 ----------
        val syntactic = mutableListOf<Triple<RawCourse, RawSession, WeekSet>>()
        for (course in raw.courses) {
            for (session in course.sessions) {
                when (val outcome = parser.tryParse(session.weeksRaw, totalWeeks = null)) {
                    is WeekParseOutcome.Success -> syntactic += Triple(course, session, outcome.weekSet)
                    is WeekParseOutcome.Failure -> return fail(
                        ParseStage.WEEK_EXPRESSION,
                        "课程「${course.name}」的周次规则无法识别：${outcome.error.message}",
                    )
                }
            }
        }

        // ---------- 学期总周数：教务未给则推导，并记 import_verify_diff 提示 ----------
        val observedMaxWeeks = syntactic.flatMap { it.third.weeks }.maxOrNull() ?: 0
        if (raw.semester.totalWeeks == null) {
            issues += ImportIssue(
                kind = ImportIssue.Kind.TOTAL_WEEKS_INFERRED,
                courseName = null,
                detail = "教务系统未给出学期总周数，已按观察到的最大学期周次推导为 $observedMaxWeeks 周，请人工确认",
                raw = observedMaxWeeks.toString(),
            )
        }
        val resolvedTotalWeeks = raw.semester.totalWeeks ?: observedMaxWeeks
        if (resolvedTotalWeeks <= 0) {
            return fail(ParseStage.FIELD_MISSING, "学期总周数解析为 $resolvedTotalWeeks，无法用于周次夹逼")
        }

        // ---------- 第 2 遍：用统一的学期总周数重新解析，保证夹逼语义一致 ----------
        val plannedCourses = mutableListOf<PlannedCourse>()
        for (course in raw.courses) {
            val sessions = mutableListOf<PlannedSession>()
            for (session in course.sessions) {
                val weekday: DayOfWeek? = when (val rawWeekday = session.weekday) {
                    null -> null
                    in 1..7 -> DayOfWeek.of(rawWeekday)
                    else -> return fail(
                        ParseStage.FIELD_MISSING,
                        "课程「${course.name}」的星期取值 $rawWeekday 非法（只允许 1..7 或留空）",
                    )
                }
                val weekSet = when (val outcome = parser.tryParse(session.weeksRaw, resolvedTotalWeeks)) {
                    is WeekParseOutcome.Success -> outcome.weekSet
                    is WeekParseOutcome.Failure -> return fail(
                        ParseStage.WEEK_EXPRESSION,
                        "课程「${course.name}」的周次规则在学期范围 1..$resolvedTotalWeeks 内无有效周次：" +
                            outcome.error.message,
                    )
                }
                sessions += PlannedSession(
                    weekday = weekday,
                    periodStart = session.periodStart,
                    periodEnd = session.periodEnd,
                    startTime = parseTime(session.startTime, course.name, "开始时间"),
                    endTime = parseTime(session.endTime, course.name, "结束时间"),
                    campus = session.campus?.trim()?.takeIf { it.isNotEmpty() },
                    room = session.room?.trim()?.takeIf { it.isNotEmpty() },
                    teacher = session.teacher?.trim()?.takeIf { it.isNotEmpty() },
                    weeksRaw = session.weeksRaw.trim(),
                    weekNumbers = weekSet,
                    remark = session.remark,
                )
            }
            collectCourseIssues(course, sessions, issues)
            plannedCourses += PlannedCourse(
                name = course.name,
                code = course.code?.trim()?.takeIf { it.isNotEmpty() },
                totalHours = course.totalHours,
                isOnline = course.isOnline,
                sessions = sessions.toList(),
            )
        }

        return AppResult.success(
            ImportPlan(
                semester = raw.semester.copy(startDate = startDate, totalWeeks = resolvedTotalWeeks),
                courses = plannedCourses.toList(),
                issues = issues.toList(),
                // 透传原始载荷路径：解析现场必须能随批次行落库（import_batches.payload_ref）。
                payloadRef = raw.payloadRef,
            )
        )
    }

    /**
     * 两条安排是否落在**同一个时段** —— 周次重叠告警的唯一前置判据。
     *
     * 为什么要它（真机回归）：教务系统把同一门课按**不同时间段**分别排课，
     * 例如"周一 1-2 节"与"周二 3-4 节"各有各的周次段，它们的周次重叠是**完全正常**的。
     * 旧实现把同一门课的全部安排两两求周次交集、完全不看时段，于是一次导入刷出
     * 50 条 `OVERLAPPING_WEEKS` 误报，把"同一时段真的撞车"这唯一有风险的信号淹没了。
     *
     * 判据：`weekday` 相同 且 节次区间相同；节次缺失时退化为 `startTime` 相同。
     * 时段信息都缺失时返回 false —— **宁可漏报也不误报**：误报会淹掉真信号，
     * 而漏报的那类（时间待定的两条）本来也无法判定冲突。
     *
     * 刻意做成 `internal`（而非 private）：判据本身必须可被单测直接覆盖，
     * 这不改变类的公共签名。
     */
    internal fun sameSlot(a: PlannedSession, b: PlannedSession): Boolean {
        if (a.weekday != b.weekday) return false

        val aPeriod = a.periodStart
        val bPeriod = b.periodStart
        if (aPeriod != null && bPeriod != null) {
            return aPeriod == bPeriod && a.periodEnd == b.periodEnd
        }

        // 节次缺失（教务页面不给节次的情形）：退化为"上课时间相同"。
        val aStart = a.startTime ?: return false
        val bStart = b.startTime ?: return false
        return aStart == bStart
    }

    /** 时段的人类可读描述，写进告警文案以便用户直接核对（如"周一 第1-2节"）。 */
    private fun slotLabel(session: PlannedSession): String {
        val day = session.weekday?.let(SemesterFormatting::weekday) ?: "无固定星期"
        return when {
            session.periodStart != null && session.periodEnd != null ->
                "$day 第${session.periodStart}-${session.periodEnd}节"
            session.periodStart != null -> "$day 第${session.periodStart}节"
            session.startTime != null -> "$day ${session.startTime}"
            else -> "$day 时间待定"
        }
    }

    /**
     * 同一门课的问题收集。
     *
     * 周次重叠**只在同时段内判定**：不同时间段的周次重叠是教务的正常排课，不是冲突。
     * 其余提示（缺时间 / 缺教室）逐条独立判定，与时段无关。
     */
    private fun collectCourseIssues(
        course: RawCourse,
        sessions: List<PlannedSession>,
        issues: MutableList<ImportIssue>,
    ) {
        sessions.forEachIndexed { index, session ->
            for (otherIndex in index + 1 until sessions.size) {
                val other = sessions[otherIndex]
                if (!sameSlot(session, other)) continue
                val overlap = session.weekNumbers.weeks intersect other.weekNumbers.weeks
                if (overlap.isNotEmpty()) {
                    issues += ImportIssue(
                        kind = ImportIssue.Kind.OVERLAPPING_WEEKS,
                        courseName = course.name,
                        detail = "同一门课在同一时段（${slotLabel(session)}）的两条安排周次重叠：" +
                            "第 ${overlap.sorted().joinToString(",")} 周同时命中",
                        raw = "${session.weeksRaw} / ${other.weeksRaw}",
                    )
                }
            }
            if (session.startTime == null && session.periodStart == null) {
                issues += ImportIssue(
                    kind = ImportIssue.Kind.MISSING_TIME,
                    courseName = course.name,
                    detail = "该条安排没有节次也没有时间，将以「时间待定」呈现",
                    raw = session.weeksRaw,
                )
            }
            if (!course.isOnline && session.room.isNullOrBlank()) {
                issues += ImportIssue(
                    kind = ImportIssue.Kind.MISSING_ROOM,
                    courseName = course.name,
                    detail = "该条安排没有教室，将以「地点待定」呈现",
                    raw = session.weeksRaw,
                )
            }
        }
    }

    /**
     * 时间解析：先试 `H:mm`（教务常见），再试 `HH:mm:ss`（少数页面）。
     *
     * 两种都失败时**抛错而不是返回 null** —— 返回 null 会让该课次悄悄变成"时间待定"，
     * 而真实原因是教务给了一个我们不认识的格式。这正是"沉默逻辑错误"的典型形态
     * （`generated-code-failure-modes.md` 第 2 节）。
     */
    private fun parseTime(value: String?, courseName: String, field: String): LocalTime? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (formatter in TIME_FORMATTERS) {
            try {
                return LocalTime.parse(text, formatter)
            } catch (_: DateTimeParseException) {
                // 只吞"格式不匹配"这一种异常，继续试下一种格式；两种都不匹配时下面的抛错会兜住。
            }
        }
        throw IllegalArgumentException("课程「$courseName」的$field '$text' 无法解析（期望 H:mm 或 HH:mm:ss）")
    }

    private fun fail(stage: ParseStage, detail: String): AppResult.Failure =
        AppResult.Failure(AppError.Parse(stage, detail))

    private companion object {
        val TIME_FORMATTERS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
        )
    }
}
