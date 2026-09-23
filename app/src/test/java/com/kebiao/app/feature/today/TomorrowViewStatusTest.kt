package com.kebiao.app.feature.today

import com.kebiao.app.Fixtures
import com.kebiao.app.domain.model.ClassStatus
import com.kebiao.app.domain.model.SessionOccurrence
import com.kebiao.app.domain.schedule.ScheduleCalculator
import com.kebiao.app.ui.components.CourseCardState
import com.kebiao.app.ui.components.courseCardStateOf
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* =========================================================================
 * 「查看明天」的状态判定 —— 锁死一个差一整天的陷阱
 *
 * 课程卡状态机 [courseCardStateOf] 与 domain 的 [ScheduleCalculator.statusOf]
 * 都**只用时刻判定，不看日期**。若明天视图照常把真实的当前时间传进去：
 *  - 现在 22:53，明天 08:10 的课 -> nowMin(1373) >= endMin(580) -> 「已结束」，卡片变灰；
 *  - 现在 08:00，明天 08:10 的课 -> 「即将开始」，显示「还剩 10 分钟」——差一整天。
 *
 * 因此 VM 在明天视图下传给求值算法的 now 是 [LocalTime.MIN]（00:00）。
 * 本文件断言这么做之后的结果，并**反证**不这么做会真的出错（断言 4），
 * 避免"断言 3 恒真、其实什么也没挡住"。
 * ========================================================================= */
class TomorrowViewStatusTest {

    /** 未来日期该有的样子：稍后 / 线上 / 待定，绝不出现"已结束 / 进行中 / 即将开始"。 */
    private val forbidden = setOf(
        CourseCardState.FINISHED,
        CourseCardState.ONGOING,
        CourseCardState.UPCOMING_SOON,
    )

    @Test
    fun `tomorrow at midnight yields no finished ongoing or soon cards`() {
        val items = occurrencesOn(Fixtures.wednesdayOfWeek(3))

        assertTrue(items.isNotEmpty(), "第 3 周周三应有课（大学物理B(1) 08:10）")
        items.forEach { item ->
            assertEquals(ClassStatus.UPCOMING, item.status, "${item.courseName} 在 00:00 应为未开始")
            val cardState = courseCardStateOf(item, LocalTime.MIN)
            assertTrue(
                cardState !in forbidden,
                "${item.courseName}（${item.startTime}-${item.endTime}）在明天视图下不应是 $cardState",
            )
        }
    }

    /**
     * 反证：同一批课若传真实的当前时间（23:00）就会判成「已结束」。
     * 这条必须在——否则上一条断言可能是白写的。
     */
    @Test
    fun `the same items at 23_00 really do become finished which is why midnight is used`() {
        val items = occurrencesOn(Fixtures.wednesdayOfWeek(3))
        assertTrue(items.isNotEmpty())

        val atNight = items.map { courseCardStateOf(it, LocalTime.of(23, 0)) }
        assertTrue(
            atNight.any { it == CourseCardState.FINISHED },
            "23:00 求值应至少有一门课被判为已结束——若没有，说明夹具变了，前一条断言失去意义",
        )
    }

    /**
     * 周四取大学物理实验（13:30）。
     *
     * 注意夹具里实验课的周次段是 9-11 / 12 / 13 / 14 / 15-16 —— **第 3 周没有实验课**，
     * 所以这里用第 9 周；assert 里顺带把"第 3 周周四没课"这一事实钉住，
     * 防止有人日后把周次改宽、让本条断言悄悄变成另一个意思。
     */
    @Test
    fun `thursday lab also stays upcoming under midnight`() {
        assertTrue(
            occurrencesOn(Fixtures.thursdayOfWeek(3)).isEmpty(),
            "第 3 周周四没有实验课（实验周次从 9 开始），本条断言应使用第 9 周",
        )

        val items = occurrencesOn(Fixtures.thursdayOfWeek(9))
        assertTrue(items.isNotEmpty(), "第 9 周周四应有大学物理实验 13:30")
        assertEquals(LocalTime.of(13, 30), items.first().startTime)

        items.forEach { item ->
            assertEquals(ClassStatus.UPCOMING, item.status)
            assertTrue(
                courseCardStateOf(item, LocalTime.MIN) !in forbidden,
                "${item.courseName}（${item.startTime}）在明天视图下不应出现时间态",
            )
        }
    }

    private fun occurrencesOn(date: LocalDate, now: LocalTime = LocalTime.MIN): List<SessionOccurrence> =
        ScheduleCalculator.occurrencesOn(
            date = date,
            semester = Fixtures.SEMESTER,
            sessions = Fixtures.ALL_SESSIONS,
            coursesById = Fixtures.COURSES_BY_ID,
            now = now,
        )
}
