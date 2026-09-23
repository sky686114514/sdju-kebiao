package com.kebiao.app.ui.components

import com.kebiao.app.domain.model.SessionOccurrence
import java.time.LocalTime

/* =========================================================================
 * 课程卡状态机（Spec §7 的 8 种状态）
 *
 * 卡片级 6 态：FINISHED / ONGOING / UPCOMING_SOON / LATER / PENDING / ONLINE
 * 页面级 2 态：ALL_FINISHED（今日已上完）/ NO_CLASS（今日无课）——见 TodayUiState
 *
 * 判定优先级（唯一口径，顺序不可换）：
 *   1. PENDING      无 startTime 或无 endTime（如「军事技能」）——任何情况下最高优先
 *   2. ONGOING      正在上（start <= now < end）
 *   3. FINISHED     已下课（now >= end）
 *   4. UPCOMING_SOON 距上课 <= 阈值（默认 15 分钟）
 *   5. ONLINE       纯线上课（尔雅通识 / 线上教学），给「线上」pill 而不是地点
 *   6. LATER        其余（稍后）
 *
 * 为什么 ONLINE 排在时间态之后：线上课也有具体上课时间，正在上时必须显示「进行中」，
 * 只有「还没到时间、也不需要立刻关注」时才用「线上」作为身份标识。
 *
 * 状态变化不含任何位置或尺寸变化——只有颜色与脉冲。用户正在读的那一行不会跑掉。
 * ========================================================================= */

enum class CourseCardState {
    /** 正在进行中。全屏只允许 1 门（由列表层用 derivedStateOf 保证单一性）。 */
    ONGOING,

    /** 已结束。文字降到 55% 不透明度。 */
    FINISHED,

    /** 即将开始（默认 15 分钟内）。时间行图标换 alarm，不脉冲、不改边框底色。 */
    UPCOMING_SOON,

    /** 稍后。默认态，无 pill。 */
    LATER,

    /** 待定：无固定时间地点（如「军事技能」）。归入「待定」分组，不塞进时间轴。 */
    PENDING,

    /** 纯线上课（尔雅通识 / 线上教学）。地点行显示 wifi 图标 + 线上标识。 */
    ONLINE,

    /**
     * 单双周未命中（本周不上）。
     *
     * 口径说明（AC-13 与 AC-50 优先于展示偏好）：
     *  - 今日课程**默认不得**列出未命中的课（AC-13 明文）；
     *  - 周视图**默认只渲染命中单双周规则的课**（AC-50 明文）；
     *  - 因此本状态只在用户于设置页显式打开「显示非本周课程」后，
     *    由周视图以 55% 不透明度 + 虚线边框 + 「本周不上」pill 呈现。
     */
    NOT_THIS_WEEK
}

/** 「即将开始」的默认阈值（分钟）。UIUX §5.5：距上课 15 分钟内为低强度预告。 */
const val UPCOMING_SOON_MINUTES: Int = 15

/**
 * 由课次推导卡片状态。纯函数，now 由参数注入（domain 铁律：不在 UI 里调 now()）。
 */
fun courseCardStateOf(
    occurrence: SessionOccurrence,
    now: LocalTime,
    leadMinutes: Int = UPCOMING_SOON_MINUTES
): CourseCardState {
    val start = occurrence.startTime
    val end = occurrence.endTime

    // 1) 无时间 => 待定
    if (start == null || end == null) return CourseCardState.PENDING

    val nowMin = now.hour * 60 + now.minute
    val startMin = start.hour * 60 + start.minute
    val endMin = end.hour * 60 + end.minute

    // 2) 进行中
    if (nowMin in startMin until endMin) return CourseCardState.ONGOING

    // 3) 已结束
    if (nowMin >= endMin) return CourseCardState.FINISHED

    // 4) 即将开始
    if (startMin - nowMin <= leadMinutes.coerceAtLeast(0)) return CourseCardState.UPCOMING_SOON

    // 5) 纯线上
    if (occurrence.isOnline) return CourseCardState.ONLINE

    // 6) 稍后
    return CourseCardState.LATER
}

/** 距上课还有几分钟（用于「N 分钟后」pill 文案）。仅对 UPCOMING_SOON 有意义。 */
fun minutesUntilStart(occurrence: SessionOccurrence, now: LocalTime): Int? {
    val start = occurrence.startTime ?: return null
    val nowMin = now.hour * 60 + now.minute
    val startMin = start.hour * 60 + start.minute
    return (startMin - nowMin).coerceAtLeast(0)
}
