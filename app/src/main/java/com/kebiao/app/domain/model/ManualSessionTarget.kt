package com.kebiao.app.domain.model

/**
 * 手动编辑的写入目标。
 *
 * 用密封类型而非 `sessionId: Long?` 表达"新增还是改已有"：
 * 后者在 `id = 0` 与"真的有 id=0 的行"之间语义含糊，是典型的边界 bug 温床。
 */
sealed interface ManualSessionTarget {

    /** 在指定课程下新增一条 [ClassSession]。 */
    data class New(val courseId: Long) : ManualSessionTarget

    /** 修改已有课次；仓储会校验它必须是 MANUAL 来源（AC-41）。 */
    data class Existing(val sessionId: Long) : ManualSessionTarget
}
