package com.kebiao.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.repository.ScheduleRepository
import com.kebiao.app.data.repository.SemesterRepository
import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.schedule.ScheduleCalculator
import com.kebiao.app.domain.schedule.SemesterFormatting
import com.kebiao.app.ui.components.formatTimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime

/* =========================================================================
 * 课程详情 ViewModel
 *
 * 数据来源是**当前学期的全部课次与课程**，而不是"今天"：
 * 从周视图点进来的课次不属于今天，只查今天会取不到数据（点进去是空壳）。
 * 因此这里按 `session_id` 在学期范围内定位，并**额外**判断它是否命中今天，
 * 命中时才算当次状态（进行中 / 即将开始）。
 *
 * 取不到时给出显式缺失态，不显示半截数据。
 * ========================================================================= */

data class CourseDetailUiState(
    val loading: Boolean = true,
    /** 是否在当前学期里找到了该课次。false = 数据已被切换学期或重新导入替换掉。 */
    val found: Boolean = false,
    val courseName: String = "",
    val courseCode: String? = null,
    /** 来自 domain 的 `Course.accentIndex`，与课表卡片、小组件同一套配色索引。 */
    val accentIndex: Int = 0,
    val isOnline: Boolean = false,
    /** 「周三」；无固定星期为 null。 */
    val weekdayLabel: String? = null,
    /** 「08:10-09:40」；无时间为「时间待定」。 */
    val timeLabel: String = "",
    /** 「1-2 节」；无节次为 null。 */
    val periodLabel: String? = null,
    /** 「第 3-16 周」/「第 2,4,6 周」。 */
    val weeksLabel: String = "",
    val campus: String? = null,
    val room: String? = null,
    val teacher: String? = null,
    val remark: String? = null,
    val semesterName: String = "",
    /** 该课次命中今天时的求值结果；不命中为 null（详情页照常展示，只是不显示当次状态）。 */
    val todayOccurrence: SessionOccurrence? = null,
    val now: LocalTime = LocalTime.MIDNIGHT
)

@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModel(
    semesterRepository: SemesterRepository,
    scheduleRepository: ScheduleRepository,
    private val timeProvider: TimeProvider,
    private val sessionId: Long
) : ViewModel() {

    val uiState: StateFlow<CourseDetailUiState> = combine(
        semesterRepository.observeCurrent(),
        scheduleRepository.observeSessionsOfCurrentSemester(),
        scheduleRepository.observeCoursesOfCurrentSemester(),
        timeProvider.todayFlow(),
        timeProvider.nowFlow()
    ) { semester, sessions, courses, today, now ->
        reduce(semester, sessions, courses, today, now)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CourseDetailUiState()
        )

    private fun reduce(
        semester: Semester?,
        sessions: List<ClassSession>,
        courses: Map<Long, Course>,
        today: LocalDate,
        now: LocalTime
    ): CourseDetailUiState {
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return CourseDetailUiState(loading = false, found = false)
        val course = courses[session.courseId]
            ?: return CourseDetailUiState(loading = false, found = false)

        val week: Int? = semester?.weekOf(today)
        val hitsToday = session.weekday == today.dayOfWeek &&
            week != null &&
            week in session.weekNumbers

        return CourseDetailUiState(
            loading = false,
            found = true,
            courseName = course.name,
            courseCode = course.code,
            accentIndex = course.accentIndex,
            isOnline = course.isOnline,
            weekdayLabel = session.weekday?.let(SemesterFormatting::weekday),
            timeLabel = formatTimeRange(session.startTime, session.endTime).ifEmpty { "时间待定" },
            periodLabel = SemesterFormatting.periods(session),
            weeksLabel = "第 ${session.weekNumbers.display()} 周",
            campus = session.campus,
            room = session.room,
            teacher = session.teacher,
            remark = session.remark,
            semesterName = semester?.name.orEmpty(),
            // hitsToday 的判定里已经含 `week != null`，编译器据此把 week 智能转换为非空；
            // 再写一次 `week != null` 是恒真条件（静态检查会告警），因此这里不重复。
            todayOccurrence = if (hitsToday) {
                ScheduleCalculator.occurrence(session, course, today, week, now)
            } else {
                null
            },
            now = now
        )
    }
}
