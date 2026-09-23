package com.kebiao.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kebiao.app.core.AppError
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.repository.ScheduleRepository
import com.kebiao.app.data.repository.SemesterRepository
import com.kebiao.app.domain.model.ClassStatus
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.model.TodayEmptyReason
import com.kebiao.app.domain.model.TodaySchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/* =========================================================================
 * 今日课程 ViewModel
 *
 * 关键纪律（Spec §11 / ADR-001）：
 *  - UI 只订阅 Flow，不缓存、不自己算「今天」。三种"没有课"的判定权在
 *    domain 的 `ScheduleCalculator.todaySchedule`，本类**只做状态映射**
 *    （消费 `TodaySchedule.emptyReason`），不重新推导一遍——重推 = 两套口径 = 沉默逻辑错误。
 *  - 「现在」一律经 TimeProvider 注入，UI 层与 ViewModel 层都不调 `LocalTime.now()`。
 *  - 每分钟对齐 tick 驱动「即将开始 -> 进行中」的状态切换（UIUX §5.5），不用长轮询。
 *  - 下拉刷新**真的重新订阅** Room Flow（flatMapLatest 重启查询），
 *    而不是只把指示器转一圈——后者是"假装刷新"。
 *
 * 「查看明天」：本类只持有一个 [viewingTomorrow] 布尔，日期由它和注入时钟推出
 * （`if (viewingTomorrow) today + 1 else today`）。求值仍然走同一个
 * `ScheduleRepository.observeDay`，不新增第二套日期口径。明天视图传给求值算法的
 * 「现在」是 00:00 —— 原因见 [nowForDay]，别改回去。
 * ========================================================================= */

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val semesterRepository: SemesterRepository,
    private val scheduleRepository: ScheduleRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val refreshing = MutableStateFlow(false)

    /**
     * 是否正在看「明天」。用 Boolean 而不是 `dayOffset: Int`：
     * 布尔只有两种取值，结构上不可能出现「+2 天 / -1 天」这类无意义状态。
     */
    private val viewingTomorrowFlag = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = refreshing

    /** 当前是否在看明天（供 TopAppBar 换标题与切换按钮图标）。 */
    val viewingTomorrow: StateFlow<Boolean> = viewingTomorrowFlag.asStateFlow()

    /** 在「今天」与「明天」之间切换。 */
    fun toggleTomorrow() {
        viewingTomorrowFlag.value = !viewingTomorrowFlag.value
    }

    /**
     * 对齐到下一分钟的时间流，只携带「现在几点」。
     * 不使用 `System.currentTimeMillis()` 做对齐——对齐量由注入时钟的秒数算出，
     * 这样时间源可替换（单测 / 桌面预览）。
     */
    private val clock = flow {
        while (true) {
            val now = timeProvider.now()
            emit(now)
            delay((60 - now.second).coerceAtLeast(1) * 1_000L)
        }
    }

    /** 当前查看的日期：今天或明天。跨零点时 todayFlow 重发，明天会自动跟着挪一天。 */
    private val selectedDate = combine(viewingTomorrowFlag, timeProvider.todayFlow()) { tomorrow, today ->
        if (tomorrow) today.plusDays(1) else today
    }

    /**
     * 用于状态判定的「现在」。
     *
     * **明天视图下传 [LocalTime.MIN]（00:00）**，这不是手滑：
     * 课程卡状态机 [com.kebiao.app.ui.components.courseCardStateOf] 只用**时刻**判定、
     * 不看日期。若明天视图照常传真实的当前时间：
     *  - 现在 22:53，明天 08:10 的课 -> `nowMin(1373) >= endMin(580)` -> 判成 **已结束**，
     *    卡片变灰 55% 不透明度；
     *  - 现在 08:00，明天 08:10 的课 -> 判成**即将开始**，显示「还剩 10 分钟」——差一整天。
     * 传 00:00 后 domain 的 `ScheduleCalculator.statusOf` 把全部课判为 UPCOMING，
     * 卡片状态机进一步收敛为「稍后 / 线上 / 待定」，正是未来日期该有的样子；
     * 「还剩 N 分钟」pill 也自然不显示（CourseCard 只在 UPCOMING_SOON 时使用它）。
     *
     * [distinctUntilChanged] 的用途：明天视图下这个流恒为 00:00，
     * 不去重会让下游每分钟收到一个完全相同的「现在」并白重组一次。
     */
    private val nowForDay = combine(viewingTomorrowFlag, clock) { tomorrow, now ->
        if (tomorrow) LocalTime.MIN else now
    }.distinctUntilChanged()

    /** 下拉刷新：重启 Room 查询，使"刷新"有真实效果（Room 会对新订阅者重发当前快照）。 */
    private val todaySchedule = refreshTrigger
        .flatMapLatest { scheduleRepository.observeDay(selectedDate, nowForDay) }

    /**
     * 「下节课」要跨天找，所以按"学期 + 课次 + 课程 + 正在看的那一天 + 该天的现在"重新订阅。
     * 起点用 [selectedDate] 而不是"今天"、时刻用 [nowForDay] 而不是 `timeProvider.now()`，
     * 这样看明天时"下节课"是相对明天找的（而非相对今天）。
     */
    private val upcoming = combine(
        semesterRepository.observeCurrent(),
        scheduleRepository.observeSessionsOfCurrentSemester(),
        scheduleRepository.observeCoursesOfCurrentSemester(),
        selectedDate,
        nowForDay
    ) { semester, sessions, courses, viewingDate, nowOfDay ->
        UpcomingClassFinder.find(
            today = viewingDate,
            now = nowOfDay,
            semester = semester,
            sessions = sessions,
            coursesById = courses
        )
    }

    val uiState: StateFlow<TodayUiState> = combine(
        semesterRepository.observeCurrent(),
        todaySchedule,
        upcoming,
        nowForDay,
        refreshTrigger
    ) { semester, today, nextClass, now, _ ->
        reduce(today = today, nextClass = nextClass, now = now)
    }
        .catch { throwable ->
            emit(TodayUiState.Error(AppError.Unknown(throwable), retryable = true))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState.Loading
        )

    /** 下拉刷新 / 重试。 */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            refreshTrigger.value = refreshTrigger.value + 1
            // 让指示器有可见的最短时长，避免闪一下反而像故障
            delay(400)
            refreshing.value = false
        }
    }

    /**
     * 领域结果 -> 界面状态。**不重算日期、不重推周次**，只做分支映射。
     */
    private fun reduce(
        today: TodaySchedule,
        nextClass: SessionOccurrence?,
        now: LocalTime
    ): TodayUiState {
        val semester = today.semester
        val week = today.weekOfSemester

        // 尚未导入：没有任何学期，或学期存在但库里一条课次都没有
        if (semester == null || today.emptyReason == TodayEmptyReason.NOT_IMPORTED) {
            return TodayUiState.NotImported
        }

        if (today.items.isEmpty()) {
            return when {
                today.emptyReason == TodayEmptyReason.OUTSIDE_SEMESTER -> TodayUiState.OutsideSemester(
                    notStartedYet = today.date.isBefore(semester.startDate),
                    semesterName = semester.name,
                    startDate = semester.startDate
                )

                // NO_CLASS_TODAY 由 domain 保证 week 非空；week 意外为 null 时降级为"未导入"
                // 而不是硬凑一个周次——宁可显示明确的"还没有课表"，也不显示错误的"第 1 周"。
                today.emptyReason == TodayEmptyReason.NO_CLASS_TODAY && week != null -> TodayUiState.NoClassToday(
                    semesterId = semester.id,
                    semesterName = semester.name,
                    date = today.date,
                    week = week,
                    totalWeeks = semester.totalWeeks,
                    nextClass = nextClass
                )

                else -> TodayUiState.NotImported
            }
        }

        // 今日已上完：只统计有时间的课次，否则「军事技能」（无时间）会让判定失真
        val timed = today.items.filter { it.startTime != null && it.endTime != null }
        val allFinished = timed.isNotEmpty() && timed.all { it.status == ClassStatus.FINISHED }

        return TodayUiState.Content(
            semesterId = semester.id,
            semesterName = semester.name,
            date = today.date,
            week = week,
            totalWeeks = semester.totalWeeks,
            now = now,
            items = today.items,
            allFinished = allFinished
        )
    }
}
