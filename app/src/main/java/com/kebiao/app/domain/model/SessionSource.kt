package com.kebiao.app.domain.model

/**
 * 一条 [ClassSession] 的来源。
 *
 * 落库为 INTEGER（Spec 第 6 节 `class_sessions.source`）。区分来源的意义在于重复导入策略：
 * 重新导入同一学期时，**IMPORT 来源整体替换，MANUAL 来源保留**（AC-41：重新导入不得静默覆盖手编项）。
 */
enum class SessionSource(val code: Int) {
    IMPORT(0),
    MANUAL(1);

    companion object {
        fun fromCode(code: Int): SessionSource =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("未知的 SessionSource code: $code")
    }
}
