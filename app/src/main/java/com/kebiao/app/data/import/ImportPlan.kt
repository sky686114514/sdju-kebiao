package com.kebiao.app.data.import

import com.kebiao.app.domain.model.WeekSet
import com.kebiao.app.domain.schedule.SemesterFormatting

/**
 * 导入计划 —— "将要写库的内容"的完整描述，也是**核对视图的数据源**。
 *
 * 强制 Preview 环节的技术载体：`ImportMapper` 只产出 [ImportPlan]，
 * 不经用户确认就不写库（ARCHITECTURE.md 第 7.6 节：Preview 不可跳过）。
 */
data class ImportPlan(
    val semester: SemesterDraft,
    val courses: List<PlannedCourse>,
    /** 非致命问题（会照常入库，但必须在核对视图上高亮）。 */
    val issues: List<ImportIssue>,
    /**
     * 原始 HTML / JSON 的本地留存路径（`RawSchedule.payloadRef`）。
     *
     * 随计划一路带到 [ImportCommitter]，落进 `import_batches.payload_ref`：
     * 解析结果可疑时，用户能凭它打开原始页面/文件核对现场，这是"可返工"的唯一线索。
     * 刻意不给默认值 —— 漏传会导致现场丢失且毫无信号（沉默错误），必须显式传。
     */
    val payloadRef: String?,
) {
    val sessionCount: Int get() = courses.sumOf { it.sessions.size }

    /**
     * 学期总周数。Mapper 阶段必然已解析出具体值（教务未给时由 `max(weeks)` 推导），
     * 因此这里**不允许回落成 0** —— 静默的 0 会让全部周次被夹逼成空集，是本项目最怕的沉默逻辑错误。
     */
    val totalWeeks: Int
        get() = requireNotNull(semester.totalWeeks) {
            "ImportPlan.totalWeeks 必须在 ImportMapper 阶段解析完成，不得留空"
        }
}

data class PlannedCourse(
    val name: String,
    val code: String?,
    val totalHours: Int?,
    val isOnline: Boolean,
    val sessions: List<PlannedSession>,
)

/**
 * 一条已解析的上课安排。
 *
 * [weeksRaw] 与 [weekNumbers] 必须同时保留：前者是审计回溯的唯一依据，
 * 后者是求值用的规范化集合 —— 这正是 Spec 第 6 节同时保留 `weeks_raw` 与 `week_numbers` 两列的理由。
 */
data class PlannedSession(
    val weekday: java.time.DayOfWeek?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: java.time.LocalTime?,
    val endTime: java.time.LocalTime?,
    val campus: String?,
    val room: String?,
    val teacher: String?,
    val weeksRaw: String,
    val weekNumbers: WeekSet,
    val remark: String?,
)

/**
 * 导入过程中的提示项。
 *
 * 与"导入失败"严格区分：致命问题走 `AppResult.Failure`，会让整批导入中止；
 * [ImportIssue] 只表示"入库了但不完全对"，必须在核对视图显示（对应 PRD 第 5.3 节第 2 痛点）。
 */
data class ImportIssue(
    val kind: Kind,
    val courseName: String?,
    val detail: String,
    /** 原始串 / 原始值，供人工核对。 */
    val raw: String?,
) {
    enum class Kind {
        /** 学期总周数未由教务给出，已由 `max(weeks)` 推导。 */
        TOTAL_WEEKS_INFERRED,

        /** 同一门课出现周次重叠的两条课次（通常是教务页面理解偏差）。 */
        OVERLAPPING_WEEKS,

        /** 缺少节次或时间（会以"时间待定"呈现）。 */
        MISSING_TIME,

        /** 缺少教室（线上课除外）。 */
        MISSING_ROOM,
    }
}

/** 导入提交结果。 */
data class ImportResult(
    val batchId: Long,
    val semesterId: Long,
    val courseCount: Int,
    val sessionCount: Int,
    val issues: List<ImportIssue>,
)

/**
 * 核对视图快照：只含 UI 需要的只读信息，避免 UI 直接操作计划对象。
 */
data class ImportPreview(
    val semesterName: String,
    val semesterLabel: String,
    val totalWeeks: Int,
    val courseCount: Int,
    val sessionCount: Int,
    val rows: List<PreviewRow>,
    val issues: List<ImportIssue>,
) {
    /** 核对视图的一行：课程 + 时间 + 周次 + 教室 + 教师（PRD 第 5.3 节要求"能一眼核对"）。 */
    data class PreviewRow(
        val courseName: String,
        val sessionLabel: String,
        val weeksDisplay: String,
        val roomDisplay: String,
        val teacherDisplay: String,
    )
}

/** 由 [ImportPlan] 派生核对视图数据。 */
fun ImportPlan.toPreview(): ImportPreview = ImportPreview(
    semesterName = semester.name,
    semesterLabel = "${semester.academicYear} 学年 / 第 ${semester.termIndex} 学期",
    totalWeeks = totalWeeks,
    courseCount = courses.size,
    sessionCount = sessionCount,
    rows = courses.flatMap { course ->
        course.sessions.map { session ->
            ImportPreview.PreviewRow(
                courseName = course.name,
                sessionLabel = buildString {
                    append(session.weekday?.let(SemesterFormatting::weekday) ?: "无固定星期")
                    when {
                        session.periodStart != null && session.periodEnd != null ->
                            append(" 第 ${session.periodStart}-${session.periodEnd} 节")
                        session.periodStart != null -> append(" 第 ${session.periodStart} 节")
                        else -> append(" 节次待定")
                    }
                },
                weeksDisplay = session.weekNumbers.display(),
                roomDisplay = when {
                    course.isOnline -> "线上"
                    session.room.isNullOrBlank() -> "地点待定"
                    else -> session.room
                },
                teacherDisplay = session.teacher?.takeIf { it.isNotBlank() } ?: "教师待定",
            )
        }
    },
    issues = issues,
)
