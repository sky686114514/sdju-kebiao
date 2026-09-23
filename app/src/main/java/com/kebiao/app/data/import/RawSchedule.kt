package com.kebiao.app.data.import

import com.kebiao.app.domain.model.ImportSourceKind
import java.time.LocalDate

/**
 * 导入路径的统一中间表示。
 *
 * 三条数据入口（WebView 同源读取 / 电脑脚本导出的 JSON 文件 / 手动编辑）汇入同一个
 * `ImportMapper`，因此**校验、周次解析、核对视图只有一份实现**（ARCHITECTURE.md 第 7.4 节）。
 *
 * 时间字段刻意保持 `String`：把"格式是否合法"的判断留在 Mapper 一处，
 * 而不是在 HTML 解析器、JSON 反序列化器里各写一遍。
 */
data class RawSchedule(
    val kind: ImportSourceKind,
    val semester: SemesterDraft,
    val courses: List<RawCourse>,
    /** 原始 HTML / JSON 的本地文件路径；**不外传**，仅用于排查与"保留现场"。 */
    val payloadRef: String?,
    /** 导出脚本写入的时间戳（ISO-8601），缺失为 null。 */
    val exportedAt: String? = null,
)

/**
 * 学期草稿。
 *
 * [startDate] 与 [totalWeeks] 允许为空 —— 教务系统有时不给学期总周数；
 * 拿到 null 时**不是丢弃，而是推导并记 `import_verify_diff`**（ARCHITECTURE.md 第 6.4 节边界用例 9）。
 * 但 [startDate] 缺失无法推导（"今天是第几周"完全依赖它），Mapper 会显式报错。
 */
data class SemesterDraft(
    val name: String,
    val academicYear: String,
    val termIndex: Int,
    val startDate: LocalDate?,
    val totalWeeks: Int?,
)

data class RawCourse(
    val name: String,
    val code: String?,
    val totalHours: Int?,
    val isOnline: Boolean,
    val sessions: List<RawSession>,
)

/**
 * 一条原始上课安排（尚未解析周次、尚未判定星期合法性）。
 *
 * @param weekday 1..7；null 表示无固定星期
 * @param weeksRaw 原始周次串，例如 `2-16 双周`（必须原样保留以便审计回溯）
 */
data class RawSession(
    val weekday: Int?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: String?,
    val endTime: String?,
    val campus: String?,
    val room: String?,
    val teacher: String?,
    val weeksRaw: String,
    val remark: String? = null,
)
