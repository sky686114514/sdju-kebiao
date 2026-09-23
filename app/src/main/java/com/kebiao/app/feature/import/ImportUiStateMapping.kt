package com.kebiao.app.feature.import

import com.kebiao.app.data.import.ImportState

/* =========================================================================
 * 数据层状态 -> 展示状态
 *
 * 从 `ImportViewModel` 拆出来，只为守住「单文件 ≤300 行」。
 *
 * 映射纪律：这里是**纯函数**，不触碰 ViewModel 的任何实例状态（诊断字段的
 * 携带由 VM 的 `carryLoginDiagnostics()` 负责）。两套职责分开，映射才可单测。
 * ========================================================================= */

internal fun ImportState.toUiState(): ImportUiState = when (this) {
    ImportState.Idle -> ImportUiState.Idle

    // 真人在 WebView 里登录；数据源正在挂起等待载荷注入
    ImportState.AwaitingLogin -> ImportUiState.LoggingIn()
    ImportState.Fetching -> ImportUiState.LoggingIn()

    ImportState.Parsing -> ImportUiState.Fetching(step = "正在解析课表", progress = null)

    is ImportState.Preview -> ImportUiState.Verifying(preview.toUiPreview())

    ImportState.Committing -> ImportUiState.Committing

    is ImportState.Done -> ImportUiState.Success(
        courseCount = result.courseCount,
        sessionCount = result.sessionCount
    )

    is ImportState.Error -> ImportUiState.Failed(error = error, retryable = retryable)
}

/**
 * 把抽取统计与逐格证据注入**核对态**的 preview。
 *
 * 与 `carryLoginDiagnostics()` 同款：只做字段搬运，纯函数，不碰 ViewModel 实例状态
 * （那部分由 VM 负责）。非 Verifying 状态原样返回。
 *
 * 为什么不在 `toUiPreview()` 里读：那会让映射函数依赖 VM，映射就再也不纯了。
 */
internal fun ImportUiState.carryPreviewDiagnostics(
    readStats: String?,
    cellEvidence: String?
): ImportUiState {
    val verifying = this as? ImportUiState.Verifying ?: return this
    return verifying.copy(
        preview = verifying.preview.copy(
            readStats = readStats,
            cellEvidence = cellEvidence
        )
    )
}

/**
 * 数据层核对快照 -> UI 核对模型。
 *
 * 数据层的 `ImportPreview` 是「一行一条上课安排」，而核对视图按课程分组：
 * 一屏之内先看到"有几门课、每门几条"，再逐条核对周次 / 教室 / 教师。
 * 分组只做展示聚合，不改变任何数据（不合并、不丢弃）。
 */
internal fun com.kebiao.app.data.import.ImportPreview.toUiPreview(): ImportPreview {
    val grouped = rows.groupBy { it.courseName }
    return ImportPreview(
        courseCount = courseCount,
        sessionCount = sessionCount,
        courses = grouped
            .map { (name, courseRows) ->
                ImportCourseSummary(
                    name = name,
                    sessionCount = courseRows.size,
                    detailLines = courseRows.map { row ->
                        "${row.sessionLabel} · 第 ${row.weeksDisplay} 周 · " +
                            "${row.roomDisplay} · ${row.teacherDisplay}"
                    }
                )
            }
            .sortedBy { it.name },
        warnings = issues.map { issue ->
            ImportWarning(
                courseName = issue.courseName ?: issue.kind.name,
                raw = issue.raw ?: "-",
                reason = issue.detail
            )
        }
    )
}
