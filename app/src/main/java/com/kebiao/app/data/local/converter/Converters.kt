package com.kebiao.app.data.local.converter

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Room 类型转换器。
 *
 * 只处理两种 `java.time` 类型，且**刻意采用与 Spec 第 6 节 DDL 注释一致的文本形态**：
 *  - `start_date` 存 ISO-8601 TEXT `2026-09-14`（人可读，便于直接用 sqlite3 核对）；
 *  - `start_time` / `end_time` 存 `HH:mm`（教务系统给到分钟，秒级无意义）。
 *
 * 不提供 `WeekSet` / 枚举 / Boolean 的转换器：
 *  - `week_numbers` 在实体里就是 `String`，规范化由 `WeekSet.normalized()` 在 Mapper 完成；
 *  - `source` / `status` 在实体里就是 `Int`；
 *  - Boolean 由 Room 原生按 INTEGER 处理。
 * 少一层转换 = 少一处可能被写错又不会被发现的地方。
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.format(HH_MM)

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, HH_MM) }

    private companion object {
        val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
