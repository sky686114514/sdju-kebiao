package com.kebiao.app.data.repository

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.LocalEventLog
import com.kebiao.app.core.time.TimeProvider
import com.kebiao.app.data.local.dao.ClassSessionDao
import com.kebiao.app.data.local.dao.CourseDao
import com.kebiao.app.data.repository.mapper.ClassSessionMapper
import com.kebiao.app.data.repository.mapper.CourseMapper
import com.kebiao.app.domain.model.ClassSession
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.TodaySchedule
import com.kebiao.app.domain.model.WeekSchedule
import com.kebiao.app.domain.schedule.ScheduleCalculator
import com.kebiao.app.domain.schedule.WeekScheduleBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

/**
 * 课表仓储 —— UI 只订阅 Flow，**不缓存、不自己算"今天"**（ADR-001 / Spec 第 5.2 节）。
 *
 * 刷新链路（Spec 第 5.3 节）在本类里闭合：
 * ```
 * SemesterRepository.switchTo(id)
 *   -> semesters 表变更 -> observeCurrent() 重发
 *   -> flatMapLatest 以新 semesterId 重订阅 class_sessions / courses
 *   -> combine 产生新的 TodaySchedule
 * ```
 * 关键两点：
 *  1. `observeBySemester(sem.id)` 以 `sem.id` 为查询键、外层用 `flatMapLatest` 订阅，
 *     学期切换时**旧学期的查询会被取消**，结构上不存在"新旧数据混合"（AC-20 / AC-22）。
 *  2. `todayFlow()` / `nowFlow()` 放在 **combine 的最内层**，DB Flow 只订阅一次；
 *     每分钟的时间推进不会重启数据库查询（避免无谓 IO）。
 *
 * 关于异常：数据完整性被破坏时 Flow 会抛
 * [com.kebiao.app.core.DataIntegrityException]（响亮失败，不降级为"少显示一门课"）。
 * ViewModel 可直接使用 [observeTodayResult] / [observeWeekResult] 拿到 [AppResult] 版本。
 */
class ScheduleRepository(
    private val classSessionDao: ClassSessionDao,
    private val courseDao: CourseDao,
    private val semesterRepository: SemesterRepository,
    private val timeProvider: TimeProvider,
    private val eventLog: LocalEventLog,
) {

    /** 当前学期全部 L3（[ClassSession]）。供周视图、核对视图与提醒调度复用。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSessionsOfCurrentSemester(): Flow<List<ClassSession>> =
        semesterRepository.observeCurrent().flatMapLatest { semester ->
            if (semester == null) {
                flowOf(emptyList())
            } else {
                classSessionDao.observeBySemester(semester.id)
                    .map { rows -> rows.map(ClassSessionMapper::toDomain) }
            }
        }

    /** 当前学期全部 L2（[Course]），按 id 索引（供 `coursesById` 求值入参）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCoursesOfCurrentSemester(): Flow<Map<Long, Course>> =
        semesterRepository.observeCurrent().flatMapLatest { semester ->
            if (semester == null) {
                flowOf(emptyMap())
            } else {
                courseDao.observeBySemester(semester.id)
                    .map { rows -> rows.associate { it.id to CourseMapper.toDomain(it) } }
            }
        }

    /**
     * 任意一天的课次（含状态判定）。**唯一实现**，[observeToday] 只是它绑定
     * 「今天 + 现在」的别名 —— 这样"明天 / 任意一天"与"今天"共用同一套日期口径，
     * 不会出现两套算法各自算一遍。
     *
     * [date] 与 [now] 都作为 Flow 传入（而不是取一次快照）：
     * 跨零点、用户手动切到明天、时间推进都能自然驱动重算，无需调用方重订阅。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDay(date: Flow<LocalDate>, now: Flow<LocalTime>): Flow<TodaySchedule> =
        semesterRepository.observeCurrent().flatMapLatest { semester ->
            if (semester == null) {
                date.map { d -> TodaySchedule.notImported(d) }
            } else {
                combine(
                    classSessionDao.observeBySemester(semester.id),
                    courseDao.observeBySemester(semester.id),
                    date,
                    now,
                ) { sessionRows, courseRows, d, n ->
                    val sessions = sessionRows.map(ClassSessionMapper::toDomain)
                    ScheduleCalculator.todaySchedule(
                        date = d,
                        semester = semester,
                        sessions = sessions,
                        coursesById = courseRows.associate { it.id to CourseMapper.toDomain(it) },
                        now = n,
                        hasAnySession = sessions.isNotEmpty(),
                    )
                }
            }
        }

    /** 今天的课次（含状态判定）。等价于 [observeDay] 绑定「今天 + 现在」。 */
    fun observeToday(): Flow<TodaySchedule> =
        observeDay(timeProvider.todayFlow(), timeProvider.nowFlow())

    /** 指定教学周的整周课表（含"线上 / 不排座"分区）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeek(weekIndex: Int): Flow<WeekSchedule> =
        semesterRepository.observeCurrent().flatMapLatest { semester ->
            if (semester == null) {
                flowOf(WeekScheduleBuilder.outsideSemester(null))
            } else if (weekIndex !in 1..semester.totalWeeks) {
                flowOf(WeekScheduleBuilder.outsideSemester(semester))
            } else {
                combine(
                    classSessionDao.observeBySemester(semester.id),
                    courseDao.observeBySemester(semester.id),
                    timeProvider.nowFlow(),
                ) { sessionRows, courseRows, now ->
                    WeekScheduleBuilder.build(
                        weekIndex = weekIndex,
                        semester = semester,
                        sessions = sessionRows.map(ClassSessionMapper::toDomain),
                        coursesById = courseRows.associate { it.id to CourseMapper.toDomain(it) },
                        now = now,
                    )
                }
            }
        }

    /** [observeToday] 的 [AppResult] 变体：数据损坏 -> 可展示错误，而非让 Flow 断掉。 */
    fun observeTodayResult(): Flow<AppResult<TodaySchedule>> =
        observeToday()
            .map<TodaySchedule, AppResult<TodaySchedule>> { AppResult.success(it) }
            .catch { e -> emit(AppResult.Failure(AppError.Storage(e))) }

    /** [observeWeek] 的 [AppResult] 变体。 */
    fun observeWeekResult(weekIndex: Int): Flow<AppResult<WeekSchedule>> =
        observeWeek(weekIndex)
            .map<WeekSchedule, AppResult<WeekSchedule>> { AppResult.success(it) }
            .catch { e -> emit(AppResult.Failure(AppError.Storage(e))) }

    /**
     * 提醒 / 小组件用的一次性快照：取当前学期与全部 L3。
     * 只在"重排闹钟 / 刷新小组件"这类**非 UI** 场景使用，UI 一律走 Flow。
     */
    suspend fun snapshotForScheduling(): SnapshotResult {
        val semester = when (val current = semesterRepository.currentOnce()) {
            is AppResult.Success -> current.value ?: return SnapshotResult.NoSemester
            is AppResult.Failure -> return SnapshotResult.Failed(current.error)
        }
        val sessions = try {
            classSessionDao.listBySemester(semester.id).map(ClassSessionMapper::toDomain)
        } catch (e: Exception) {
            eventLog.append(
                com.kebiao.app.core.LocalEvent(
                    name = com.kebiao.app.core.LocalEvents.ERROR_OCCURRED,
                    timestampMillis = System.currentTimeMillis(),
                    termId = semester.id,
                    attributes = mapOf("kind" to "snapshot_sessions", "cause" to (e::class.simpleName ?: "unknown")),
                )
            )
            return SnapshotResult.Failed(AppError.Storage(e))
        }
        val courses = try {
            courseDao.listBySemester(semester.id).associate { it.id to CourseMapper.toDomain(it) }
        } catch (e: Exception) {
            eventLog.append(
                com.kebiao.app.core.LocalEvent(
                    name = com.kebiao.app.core.LocalEvents.ERROR_OCCURRED,
                    timestampMillis = System.currentTimeMillis(),
                    termId = semester.id,
                    attributes = mapOf("kind" to "snapshot_courses", "cause" to (e::class.simpleName ?: "unknown")),
                )
            )
            return SnapshotResult.Failed(AppError.Storage(e))
        }
        return SnapshotResult.Loaded(semester, sessions, courses)
    }

    /** [snapshotForScheduling] 的结果。三分支显式建模，避免"null 当没数据"的含糊语义。 */
    sealed interface SnapshotResult {
        data class Loaded(
            val semester: Semester,
            val sessions: List<ClassSession>,
            /** 供提醒与小组件组装课程名/线上标记（它们拿到的只有 L3）。 */
            val courses: Map<Long, Course>,
        ) : SnapshotResult

        data object NoSemester : SnapshotResult

        data class Failed(val error: AppError) : SnapshotResult
    }
}
