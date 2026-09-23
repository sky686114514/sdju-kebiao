package com.kebiao.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * 文件版本地事件日志（PRD 第 12 节）。
 *
 * 形态：`filesDir/event-log.jsonl`，每行一条 JSON 对象（JSON Lines）。
 *
 * 为什么不用 Room 存事件：Spec 第 6 节锁定的表清单里没有事件表，加表就要加迁移；
 * 而事件日志是纯追加的诊断数据，JSONL 更简单，且可直接用文本工具查看、不需要 App。
 *
 * 隐私（Spec 第 10 节）：只写事件名、时间戳、学期 id 与调用方显式给出的属性。
 * **调用方必须保证属性里不含身份信息**；本类额外做一次兜底：敏感键名直接剔除。
 *
 * 线程模型：所有写入经单线程 executor 串行落盘。
 * 广播接收器（可能运行在主线程）调用 [append] 不会被文件 IO 阻塞。
 * 代价是刚写入的事件可能略微滞后于 [recent]，这对诊断日志是可接受的。
 * 写入失败退化为 `Log.w`，**不静默**。
 */
class FileLocalEventLog(private val context: Context) : LocalEventLog {

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kebiao-event-log").apply { isDaemon = true }
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override fun append(event: LocalEvent) {
        writer.execute {
            try {
                file.appendText(render(event) + "\n")
                pruneIfNeeded()
            } catch (e: java.io.IOException) {
                Log.w(TAG, "本地事件日志写入失败: ${e.message}")
            }
        }
    }

    /**
     * 读取最近 [limit] 条。
     *
     * 说明：只还原 `name` / `ts` / `termId` 三个字段；属性键值一律保留在文件里供人工查看，
     * 不在此处解析 —— 诊断入口的价值是"能一眼看到发生了什么"，解析全部属性没有收益。
     */
    override fun recent(limit: Int): List<LocalEvent> {
        val target = file
        if (!target.exists()) return emptyList()
        return target.readLines()
            .takeLast(limit.coerceAtLeast(1))
            .mapNotNull(::parse)
    }

    /** 只保留最近 [MAX_LINES] 行，防止长期使用后无限增长。 */
    private fun pruneIfNeeded() {
        val lines = file.takeIf { it.exists() }?.readLines() ?: return
        if (lines.size <= MAX_LINES) return
        file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    private fun render(event: LocalEvent): String = buildString {
        append('{')
        append("\"name\":\"").append(escape(event.name)).append("\",")
        append("\"ts\":").append(event.timestampMillis)
        event.termId?.let { append(",\"termId\":").append(it) }
        val safe = event.attributes.filterKeys { key -> key !in FORBIDDEN_KEYS }
        if (safe.isNotEmpty()) {
            append(",\"attributes\":{")
            append(
                safe.entries.joinToString(",") { (key, value) ->
                    "\"${escape(key)}\":\"${escape(value).take(MAX_VALUE_LENGTH)}\""
                }
            )
            append('}')
        }
        append('}')
    }

    private fun parse(line: String): LocalEvent? {
        val name = extractString(line, "name") ?: return null
        val ts = extractLong(line, "ts") ?: return null
        return LocalEvent(name = name, timestampMillis = ts, termId = extractLong(line, "termId"))
    }

    private fun extractString(line: String, key: String): String? {
        val marker = "\"$key\":\""
        val start = line.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = line.indexOf('"', from)
        if (end < 0) return null
        return line.substring(from, end)
    }

    private fun extractLong(line: String, key: String): Long? {
        val marker = "\"$key\":"
        val start = line.indexOf(marker)
        if (start < 0) return null
        var end = start + marker.length
        while (end < line.length && (line[end].isDigit() || line[end] == '-')) end++
        return line.substring(start + marker.length, end).toLongOrNull()
    }

    /** 单行 JSON 的转义：只处理会破坏结构的字符。 */
    private fun escape(value: String): String = value
        .replace("\\", "/")
        .replace("\"", "'")
        .replace("\n", " ")
        .replace("\r", " ")

    private companion object {
        const val TAG = "KebiaoEventLog"
        const val FILE_NAME = "event-log.jsonl"
        const val MAX_LINES = 2000
        const val MAX_VALUE_LENGTH = 120

        /** 即便调用方误传，也把敏感键名的值整个剔除。 */
        val FORBIDDEN_KEYS: Set<String> = setOf(
            "password", "pwd", "studentId", "student_id", "account",
            "cookie", "token", "authorization", "credential",
        )
    }
}
