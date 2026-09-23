package com.kebiao.app.domain.model

/**
 * 一条课次在"此刻"的时间状态。
 *
 * 对应 Spec 第 7 节课程卡状态机中的时间维度；"待定（无时间地点）"即 [UNKNOWN_TIME]。
 */
enum class ClassStatus {
    /** 尚未开始。 */
    UPCOMING,

    /** 正在进行中（全屏只允许 1 门，UI 侧约束）。 */
    ONGOING,

    /** 已结束。 */
    FINISHED,

    /** 无固定时间（如只给了星期没给节次，或教务未公布时间）。 */
    UNKNOWN_TIME,
}
