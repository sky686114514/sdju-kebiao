package com.kebiao.app.domain.schedule

import com.kebiao.app.domain.model.Semester
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * "当前学期"的推断（ARCHITECTURE.md 第 6.6 节）。
 *
 * 两条铁律：
 *  1. **数据源唯一**：真正的 `is_current` 存在 DB 列里。本类只负责"学期集合发生变化时推断一次"，
 *     不在每次启动时覆盖用户的手动切换（否则假期里用户切到旧学期会被自动改回去）。
 *  2. **纯函数**：`today` 由参数注入，可在单测里穷举"学期内 / 假期 / 双学期命中 / 全库为空"。
 */
object SemesterResolver {

    /**
     * 解析"当前学期"：
     *  - 已有且仅有一个 `isCurrent = true` 的学期 -> 直接采用（用户手动切换是权威）；
     *  - 否则退回 [infer] 推断。
     */
    fun resolve(semesters: List<Semester>, today: LocalDate): Semester? {
        val explicit = semesters.filter { it.isCurrent }
        if (explicit.size == 1) return explicit.single()
        return infer(semesters, today)
    }

    /**
     * 推断当前学期：
     *  1. 命中区间（`startDate <= today <= endDate`）的学期，多个则取 [Semester.termIndex] 最大者；
     *  2. 都没命中 -> 取"距今天最近的学期"，**上一个已完成学期优先于下一个未开始学期**
     *     （学期之间是假期，此时用户更可能想看刚结束的那个）；
     *  3. 全库为空 -> null。
     */
    fun infer(semesters: List<Semester>, today: LocalDate): Semester? {
        if (semesters.isEmpty()) return null

        val hitting = semesters.filter { it.weekOf(today) != null }
        if (hitting.isNotEmpty()) {
            return hitting.maxWithOrNull(compareBy({ it.termIndex }, { it.startDate }))
        }

        val past = semesters.filter { it.endDate.isBefore(today) }
            .maxWithOrNull(compareBy { it.endDate })
        val future = semesters.filter { it.startDate.isAfter(today) }
            .minWithOrNull(compareBy { it.startDate })

        return past ?: future ?: nearestByDistance(semesters, today)
    }

    /** 兜底：按"到学期区间的距离"取最近（处理学期区间完全覆盖 today 之外的极端重叠数据）。 */
    private fun nearestByDistance(semesters: List<Semester>, today: LocalDate): Semester? =
        semesters.minByOrNull { semester ->
            when {
                today.isBefore(semester.startDate) -> ChronoUnit.DAYS.between(today, semester.startDate)
                today.isAfter(semester.endDate) -> ChronoUnit.DAYS.between(semester.endDate, today)
                else -> 0L
            }
        }
}
