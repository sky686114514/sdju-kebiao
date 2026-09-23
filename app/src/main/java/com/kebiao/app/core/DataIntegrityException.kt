package com.kebiao.app.core

/**
 * 本地数据完整性被破坏（例如 `week_numbers` 里出现了非数字片段、
 * `class_sessions.course_id` 指向不存在的课程）。
 *
 * 它**不是**用户输入错误，而是程序缺陷或外部写入污染，因此必须响亮地失败而不是降级到
 * "少显示一门课"（`generated-code-failure-modes.md` 第 2 节：静默算错是最贵的一类）。
 *
 * 处理约定：数据层允许它抛出；`ScheduleRepository` 同时提供 `observe*Result()` 变体，
 * 把这些异常翻译为 [AppError.Storage]，供 ViewModel 映射到 `UiState.Error`。
 */
class DataIntegrityException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
