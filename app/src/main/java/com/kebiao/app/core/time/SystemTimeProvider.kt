package com.kebiao.app.core.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 系统时间源（生产实现）。
 *
 * 实现要点：
 *  - **不注册常驻广播接收器、不使用 AlarmManager 保活**（架构文档第 6.7 节：精确闹钟要留给上课提醒）。
 *    跨零点与改时间由"每个订阅者各起一个 60 秒 ticker"覆盖：App 不在前台时没有人收集这个 Flow，
 *    也就不会白耗电；回到前台重新订阅即立刻拿到最新值。
 *  - [zoneOverride] 仅测试用；生产留空，每次调用都重新读 [ZoneId.systemDefault()]，
 *    这样用户改系统时区后无需重启进程即可生效。
 */
class SystemTimeProvider(
    private val zoneOverride: ZoneId? = null,
    private val tick: Duration = Duration.ofSeconds(60),
) : TimeProvider {

    override fun today(): LocalDate = LocalDate.now(zone())

    override fun now(): LocalTime = LocalTime.now(zone())

    override fun zone(): ZoneId = zoneOverride ?: ZoneId.systemDefault()

    override fun todayFlow(): Flow<LocalDate> =
        ticker().map { it.toLocalDate() }.distinctUntilChanged()

    override fun nowFlow(): Flow<LocalTime> = ticker().map { it.toLocalTime() }

    private fun ticker(): Flow<LocalDateTime> = flow {
        while (currentCoroutineContext().isActive) {
            emit(LocalDateTime.now(zone()))
            delay(tick.toMillis())
        }
    }
}
