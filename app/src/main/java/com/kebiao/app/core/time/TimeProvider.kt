package com.kebiao.app.core.time

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 时间源抽象（ARCHITECTURE.md 第 6.7 节）。
 *
 * 为什么必须有这一层：
 *  - **可测**：[FixedTimeProvider] 让"第 3 周周一第 1 节课是否已下课"变成纯函数断言；
 *  - **可换**：将来加"手动查看某一天"只需换实现，求值算法一行不改；
 *  - **可评审**：代码评审时若发现 domain 层直接调 `LocalDate.now()`，即为不合格。
 *
 * 说明：[nowFlow] 是对架构文档最小接口（`today()` / `now()` / `zone()` / `todayFlow()`）的
 * 一处**显式扩展**。理由：课程卡的"进行中 / 已结束"状态随时间推进变化，
 * 若没有时间驱动的 Flow，UI 只能在数据变化时重算，导致"12:40 上完的课卡片仍显示进行中"。
 * 每 60 秒一次的时间流让状态刷新成为数据流的必然结果，与 ADR-001 的"不主动刷新"一致。
 */
interface TimeProvider {

    fun today(): LocalDate

    fun now(): LocalTime

    fun zone(): ZoneId

    /** 跨零点 / 改系统时间 / 改时区时重发（ARCHITECTURE.md 第 6.7 节）。 */
    fun todayFlow(): Flow<LocalDate>

    /** 60 秒粒度的时间流，驱动课程卡状态刷新。 */
    fun nowFlow(): Flow<LocalTime>
}
