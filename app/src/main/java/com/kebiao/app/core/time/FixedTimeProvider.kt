package com.kebiao.app.core.time

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 可任意设定的时间源（测试与预览用）。
 *
 * 架构文档第 6.7 节明确列出该类为设计的一部分：让"2026-09-28（第 3 周周一）第 1 节课是否已下课"
 * 这类断言变成纯函数测试，无需 mock 系统时钟。
 *
 * 用例见 `app/src/test/java/com/kebiao/app/domain/schedule/`。
 */
class FixedTimeProvider(
    date: LocalDate,
    time: LocalTime,
    private val fixedZone: ZoneId? = null,
) : TimeProvider {

    private val dateState = MutableStateFlow(date)
    private val timeState = MutableStateFlow(time)

    /** 当前"今天"，赋值即向所有订阅者重发（用于模拟跨零点）。 */
    var date: LocalDate
        get() = dateState.value
        set(value) { dateState.value = value }

    /** 当前"此刻"，赋值即向所有订阅者重发。 */
    var time: LocalTime
        get() = timeState.value
        set(value) { timeState.value = value }

    fun set(date: LocalDate, time: LocalTime) {
        dateState.value = date
        timeState.value = time
    }

    override fun today(): LocalDate = dateState.value

    override fun now(): LocalTime = timeState.value

    override fun zone(): ZoneId = fixedZone ?: ZoneId.systemDefault()

    override fun todayFlow(): Flow<LocalDate> = dateState.asStateFlow()

    override fun nowFlow(): Flow<LocalTime> = timeState.asStateFlow()
}
