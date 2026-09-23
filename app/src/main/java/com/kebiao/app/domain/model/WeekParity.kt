package com.kebiao.app.domain.model

/**
 * 单双周语义。
 *
 * 用于 [WeekSet] 解析阶段记录"这条周次规则原本是否带单/双周限定"，
 * 属于审计信息，不参与课次求值（求值只看已规范化好的 [WeekSet]）。
 */
enum class WeekParity {
    /** 全周（未出现单/双周限定，或显式为"每周"/"单双周"）。 */
    ALL,

    /** 单周。 */
    ODD,

    /** 双周。 */
    EVEN,
}
