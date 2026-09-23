package com.kebiao.app.data.import

import com.kebiao.app.core.AppResult
import com.kebiao.app.domain.model.ImportSourceKind

/**
 * 导入数据来源（Spec 第 5.2 节契约：`suspend fun fetch(): AppResult<RawSchedule>`）。
 *
 * 两个实现：
 *  - [WebViewImportSource]：App 内 WebView 中继登录后同源读取（主路径，ADR-003）
 *  - [JsonFileImportSource]：电脑端脚本导出的 JSON 文件（兜底路径）
 *
 * 实现约定（不可违反）：
 *  1. **不读取密码字段、不注册 `@JavascriptInterface`**（Spec 第 4.3 节强制约束 1）。
 *  2. 取数失败必须返回分类化 [com.kebiao.app.core.AppError]，不得静默返回空课表。
 *  3. 不在本层做周次解析 —— 那是 `ImportMapper` 的唯一职责。
 */
interface ImportSource {

    val kind: ImportSourceKind

    suspend fun fetch(): AppResult<RawSchedule>

    /** 释放会话/文件句柄等资源；默认无需释放。 */
    suspend fun dispose() = Unit
}
