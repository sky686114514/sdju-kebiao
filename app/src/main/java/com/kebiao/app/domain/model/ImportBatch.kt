package com.kebiao.app.domain.model

/**
 * 导入批次状态（与 Spec 第 6 节 `import_batches.status` 取值一一对应）。
 *
 * [ROLLED_BACK] 与 [FAILED] 刻意区分：前者表示"写库中途失败、已整体回滚、旧数据完好"
 * （AC-06），后者表示"取数 / 解析阶段就失败、根本没进写库流程"。
 * 把两者合成一个状态会让排查时无法判断数据是否被动过。
 */
enum class ImportBatchStatus(val code: Int) {
    RUNNING(0),
    SUCCESS(1),
    FAILED(2),
    ROLLED_BACK(3),
    ;

    companion object {
        fun fromCode(code: Int): ImportBatchStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("未知的 ImportBatchStatus code: $code")
    }
}

/** 导入数据来源（与 `import_batches.source` 取值一一对应）。 */
enum class ImportSourceKind(val code: Int) {
    /** App 内 WebView 中继登录后同源读取（主路径，ADR-003）。 */
    WEBVIEW(0),

    /** 电脑端脚本导出的 JSON 文件导入（兜底路径）。 */
    JSON_FILE(1),
    ;

    companion object {
        fun fromCode(code: Int): ImportSourceKind =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("未知的 ImportSourceKind code: $code")
    }
}
