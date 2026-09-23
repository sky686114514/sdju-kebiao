package com.kebiao.app.feature.today

import com.kebiao.app.domain.model.ClassStatus
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.Semester
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.schedule.ScheduleCalculator
import java.time.LocalDate
import java.time.LocalTime

/* =========================================================================
 * 「下节课」查找（空状态文案的数据来源）
 *
 * 为什么不自己写日期数学：这里**只做"向前扫天数"这一层循环**，
 * 每一天的求值完全复用 domain 的纯函数 [ScheduleCalculator.occurrencesOn]
 * （它内部再用 [Semester.weekOf] 判周）。
 * 因此"今天第几周 / 单双周 / 学期边界"这些易错的日期数学全项目只有一份实现。
 *
 * 为什么不在 UI 里调 now()：`now` 与 `today` 全部由 TimeProvider 经参数注入，
 * 本对象是纯函数（同样输入必然同样输出），可在 JVM 单测里穷举。
 *
 * 复杂度：最多 [LOOKAHEAD_DAYS] 次 O(n) 扫描（n = 单学期课次数，数十行），
 * 且只在「今天没有课」这条空状态路径上被调用，不在列表滚动路径上。
 * ========================================================================= */
object UpcomingClassFinder {

    /** 向前找 14 天（两周）足够覆盖"周末找下周一早八"这类最常见场景。 */
    const val LOOKAHEAD_DAYS: Int = 14

    /**
     * 找出从 [today] 起的下一门课。
     *
     * 口径：
     *  - 今天：取"尚未下课"的课里最早的一门（进行中的课也算，用户需要知道正在上什么）；
     *  - 之后每天：取当天的第一门；
     *  - 全部扫完仍没有 -> null（调用方据此不显示"下节课"行，而不是编一条假数据）。
     */
    fun find(
        today: LocalDate,
        now: LocalTime,
        semester: Semester?,
        sessions: List<com.kebiao.app.domain.model.ClassSession>,
        coursesById: Map<Long, Course>,
    ): SessionOccurrence? {
        if (semester == null || sessions.isEmpty()) return null

        for (offset in 0..LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset.toLong())
            val occurrences = ScheduleCalculator.occurrencesOn(
                date = date,
                semester = semester,
                sessions = sessions,
                coursesById = coursesById,
                now = now,
            )
            if (occurrences.isEmpty()) continue

            if (offset == 0) {
                // 今天：已下课的不算"下节课"；若今天全上完则继续往后找
                occurrences.firstOrNull { it.status != ClassStatus.FINISHED }?.let { return it }
            } else {
                return occurrences.first()
            }
        }
        return null
    }
}
