package com.kebiao.app.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.core.AppError
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.repository.ScheduleRepository
import com.kebiao.app.data.repository.SemesterRepository
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.model.WeekSchedule
import com.kebiao.app.ui.components.weekdayZh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/* =========================================================================
 * 周视图 ViewModel
 *
 * 「今天 / 当前周」全部由 TimeProvider + Semester.weekOf 得到，UI 不自算日期。
 * 「某周有哪些课」由 ScheduleRepository.observeWeek(weekIndex) 求值（domain 负责分区），
 * 本类只把 domain 结果**映射**成「整页共享的节次行表 + 7 个日列」这一渲染结构。
 *
 * 为什么行表必须整页共享：时间轴 64dp 固定在 Pager 之外，若各日列自己算行，
 * 列与时间轴会错位。行表只在这里生成一次，所有日列的 cells 与之等长且一一对应。
 * ========================================================================= */

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    private val semesterRepository: SemesterRepository,
    private val scheduleRepository: ScheduleRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    /** 用户所选周次；null = 尚未初始化，跟随「本周」。 */
    private val selectedWeek = MutableStateFlow<Int?>(null)

    private val semester: StateFlow<Semester?> = semesterRepository.observeCurrent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 当前教学周（学期外为 null）。 */
    private val currentWeek: StateFlow<Int?> =
        combine(semester, timeProvider.todayFlow()) { sem, today -> sem?.weekOf(today) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val weekSchedule = selectedWeek
        .filterNotNull()
        .flatMapLatest { index -> scheduleRepository.observeWeek(index) }

    init {
        viewModelScope.launch {
            currentWeek.collect { week ->
                if (selectedWeek.value == null && week != null) selectedWeek.value = week
            }
        }
    }

    val uiState: StateFlow<WeekUiState> =
        combine(
            semester,
            currentWeek,
            selectedWeek.filterNotNull(),
            weekSchedule
        ) { sem, current, selected, schedule ->
            reduce(semester = sem, current = current, selected = selected, schedule = schedule)
        }
            .catch { throwable ->
                emit(WeekUiState.Error(AppError.Unknown(throwable), retryable = true))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WeekUiState.Loading
            )

    fun selectWeek(week: Int) {
        selectedWeek.value = week
    }

    fun stepWeek(delta: Int, totalWeeks: Int) {
        val now = selectedWeek.value ?: 1
        selectedWeek.value = (now + delta).coerceIn(1, totalWeeks.coerceAtLeast(1))
    }

    fun goToCurrentWeek() {
        currentWeek.value?.let { selectedWeek.value = it }
    }

    private fun reduce(
        semester: Semester?,
        current: Int?,
        selected: Int,
        schedule: WeekSchedule
    ): WeekUiState {
        val totalWeeks = schedule.semester?.totalWeeks ?: semester?.totalWeeks ?: 0
        val monday = schedule.monday

        // 学期外（或学期总周数为 0）：domain 已给出空网格，这里映射成明确的空状态
        if (totalWeeks <= 0 || monday == null) {
            return WeekUiState.Empty(semesterName = semester?.name)
        }

        val allOccurrences = schedule.days.flatMap { it.items }
        val rows = WeekPeriods.resolve(allOccurrences)
        val today = timeProvider.today()

        // 行对齐：cells 与 rows 等长、同序；某一节没有课时补一个空占位行，
        // 保证所有日列在同一水平线上（否则时间轴会与网格错位）。
        val days = schedule.days.map { day ->
            DayColumnData(
                weekday = day.weekday.value,
                label = weekdayZh(day.weekday),
                isToday = selected == current && today.dayOfWeek == day.weekday,
                cells = rows.map { row ->
                    DayCell(
                        periodStart = row.periodStart,
                        occurrences = day.items.filter { it.periodStart == row.periodStart }
                    )
                }
            )
        }

        return WeekUiState.Content(
            weekIndex = selected,
            totalWeeks = totalWeeks,
            startDate = monday,
            endDate = monday.plusDays(6),
            isCurrentWeek = selected == current,
            rows = rows,
            days = days,
            // AC-51：线上课与无固定时间地点的课由 domain 放进 unscheduled，绝不硬塞进网格
            onlineOrUnplaced = schedule.unscheduled
        )
    }
}

/** 保留：周视图内部只用 DayOfWeek.entries 的顺序，避免魔法数字 1..7 散落在渲染层。 */
internal val WEEKDAY_VALUES: List<DayOfWeek> get() = DayOfWeek.entries

/** 便于日志与断言的会话计数（不参与渲染）。 */
internal val WeekUiState.Content.sessionCount: Int
    get() = days.sumOf { day -> day.cells.sumOf { it.occurrences.size } }

/** 该周是否完全没有任何课（用于区分"空学期"与"这一周没课"）。 */
internal fun List<SessionOccurrence>.hasAnySession(): Boolean = isNotEmpty()
