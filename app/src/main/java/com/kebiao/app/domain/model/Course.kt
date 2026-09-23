package com.kebiao.app.domain.model

/**
 * L2 课程（逻辑课程，"是什么课"）。
 *
 * 与 L3 [ClassSession] 是 1:N：同一门课不同周换教室/换教师时，[Course] 仍只有一条，
 * 变体全部落在 [ClassSession] 的互斥周次行上（Spec 第 2.1 节形状 1）。
 */
data class Course(
    val id: Long,
    val semesterId: Long,
    val name: String,
    /** 课程代码，如 `053017P1-15`。教务未提供时为 null（不臆造）。 */
    val code: String?,
    val totalHours: Int?,
    /** 纯线上课程（如尔雅通识课），不参与网格排布（AC-12 / AC-51）。 */
    val isOnline: Boolean,
) {
    /**
     * 课程识别色的稳定索引（0..7）。
     *
     * 铁律（Spec 第 8 节）：一色一课，课程代码稳定哈希取模 8，全 App 与 Widget 永远同色。
     * 哈希函数必须稳定 —— 不能用 [String.hashCode] 之外还会随进程变化的实现；
     * 这里显式实现一个确定性哈希，避免 JVM/ART 之外的差异，也便于单测锁定。
     * `code` 缺失时退化用课程名，保证同一门课在"有 code / 无 code"两种导入来源下仍尽量同色。
     */
    val accentIndex: Int
        get() {
            val seed = code?.takeIf { it.isNotBlank() } ?: name
            var hash = 2166136261L // FNV-1a 32bit offset basis
            for (ch in seed) {
                hash = hash xor (ch.code.toLong() and 0xFF)
                hash = (hash * 16777619L) and 0xFFFFFFFFL
            }
            return (hash % 8L).toInt()
        }
}
