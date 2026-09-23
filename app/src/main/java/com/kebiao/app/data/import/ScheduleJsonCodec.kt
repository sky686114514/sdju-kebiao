package com.kebiao.app.data.import

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.ParseStage
import com.kebiao.app.domain.model.ImportSourceKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 课表 JSON 编解码 —— WebView 同源抽取与电脑脚本导出**共用同一份 schema**。
 *
 * 格式与 `ARCHITECTURE.md` 第 7.5 节、`tools/fetch_schedule.py` 的输出一致，
 * 因此脚本产出的文件可以直接作为 App 的测试夹具与兜底导入源。
 *
 * 反序列化策略：
 *  - `ignoreUnknownKeys = true`：脚本会写入 `expected` 等仅供工具使用的附加字段；
 *  - 缺字段 / 类型不符 / `schemaVersion` 不认识，一律翻译为 [ParseStage.JSON_SCHEMA] 错误，
 *    **不允许"读进来一半"**（半截数据比失败更难排查）。
 */
object ScheduleJsonCodec {

    private const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    fun decode(text: String, kind: ImportSourceKind, payloadRef: String?): AppResult<RawSchedule> {
        val dto = try {
            json.decodeFromString(ScheduleFileDto.serializer(), text)
        } catch (e: Exception) {
            return AppResult.Failure(AppError.Parse(ParseStage.JSON_SCHEMA, "JSON 解析失败：${e.message}", e))
        }

        if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return AppResult.Failure(
                AppError.Parse(
                    ParseStage.JSON_SCHEMA,
                    "schemaVersion=${dto.schemaVersion} 不被支持（当前只支持 $SUPPORTED_SCHEMA_VERSION）",
                )
            )
        }

        val startDate = when (val parsed = parseDate(dto.semester.startDate)) {
            is DateParse.Ok -> parsed.value
            is DateParse.Bad -> return AppResult.Failure(
                AppError.Parse(ParseStage.JSON_SCHEMA, "semester.startDate '${dto.semester.startDate}' 非法：${parsed.detail}")
            )
        }

        val raw = RawSchedule(
            kind = kind,
            semester = SemesterDraft(
                name = dto.semester.name,
                academicYear = dto.semester.academicYear,
                termIndex = dto.semester.termIndex,
                startDate = startDate,
                totalWeeks = dto.semester.totalWeeks,
            ),
            courses = dto.courses.map { course ->
                RawCourse(
                    name = course.name,
                    code = course.code,
                    totalHours = course.totalHours,
                    isOnline = course.isOnline,
                    sessions = course.sessions.map { session ->
                        RawSession(
                            weekday = session.weekday,
                            periodStart = session.periodStart,
                            periodEnd = session.periodEnd,
                            startTime = session.startTime,
                            endTime = session.endTime,
                            campus = session.campus,
                            room = session.room,
                            teacher = session.teacher,
                            weeksRaw = session.weeksRaw
                                ?: return AppResult.Failure(
                                    AppError.Parse(
                                        ParseStage.JSON_SCHEMA,
                                        "课程「${course.name}」缺少 weeksRaw（周次规则不可缺省）",
                                    )
                                ),
                            remark = session.remark,
                        )
                    },
                )
            },
            payloadRef = payloadRef,
            exportedAt = dto.exportedAt,
        )
        return AppResult.success(raw)
    }

    private fun parseDate(text: String?): DateParse {
        if (text.isNullOrBlank()) return DateParse.Ok(null)
        return try {
            DateParse.Ok(LocalDate.parse(text))
        } catch (e: DateTimeParseException) {
            DateParse.Bad("期望 yyyy-MM-dd")
        }
    }

    private sealed interface DateParse {
        data class Ok(val value: LocalDate?) : DateParse
        data class Bad(val detail: String) : DateParse
    }
}

@Serializable
internal data class ScheduleFileDto(
    val schemaVersion: Int,
    val exportedAt: String? = null,
    val semester: SemesterDto,
    val courses: List<RawCourseDto> = emptyList(),
)

@Serializable
internal data class SemesterDto(
    val name: String,
    val academicYear: String,
    val termIndex: Int,
    /** `yyyy-MM-dd`；教务未公布时为 null，交由 Mapper 显式报错而不是猜。 */
    val startDate: String? = null,
    val totalWeeks: Int? = null,
)

@Serializable
internal data class RawCourseDto(
    val name: String,
    val code: String? = null,
    val totalHours: Int? = null,
    val isOnline: Boolean = false,
    val sessions: List<RawSessionDto> = emptyList(),
)

@Serializable
internal data class RawSessionDto(
    /** 1..7；空 = 无固定星期。 */
    val weekday: Int? = null,
    val periodStart: Int? = null,
    val periodEnd: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val campus: String? = null,
    val room: String? = null,
    val teacher: String? = null,
    @SerialName("weeksRaw") val weeksRaw: String? = null,
    val remark: String? = null,
)
