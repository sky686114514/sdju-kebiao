package com.kebiao.app.core

/**
 * 错误分类（ARCHITECTURE.md 第 12 节）。
 *
 * 纪律：每个失败路径都必须显式建模，并各有用例；**禁止 `catch` 吞掉**
 * （`generated-code-failure-modes.md` 第 1 节 / 第 7 节反模式 1）。
 *
 * [userMessage] 供 UI 直接展示：不出现空洞占位，也不出现 emoji（团队级 P0 规则 1）。
 */
sealed interface AppError {

    val cause: Throwable?

    /** 面向用户的文案（中文，纯文本）。 */
    val userMessage: String

    /** 无网 / 超时 / DNS / TLS 失败。 */
    data class Network(override val cause: Throwable?) : AppError {
        override val userMessage: String = "网络不可用，请检查连接后重试"
    }

    /** 鉴权失败。 */
    data class AuthFailed(
        val reason: AuthReason,
        override val cause: Throwable? = null,
    ) : AppError {
        override val userMessage: String
            get() = when (reason) {
                AuthReason.BAD_CREDENTIALS -> "账号或密码错误"
                AuthReason.NEEDS_PHONE_BINDING -> "首次登录需要在统一身份认证页面完成手机绑定"
                AuthReason.SMS_CODE_FAILED -> "短信验证码校验未通过，请重新获取"
                AuthReason.SESSION_EXPIRED -> "登录状态已过期，请重新登录"
            }
    }

    /** 解析失败：周次串非法、表格结构不匹配、字段缺失。 */
    data class Parse(
        val stage: ParseStage,
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError {
        override val userMessage: String
            get() = when (stage) {
                ParseStage.WEEK_EXPRESSION -> "周次规则无法识别：$detail"
                ParseStage.TABLE_STRUCTURE -> "课表页面结构与预期不符，教务系统可能已改版：$detail"
                ParseStage.FIELD_MISSING -> "课表缺少必要字段：$detail"
                ParseStage.JSON_SCHEMA -> "导入文件格式不符合约定：$detail"
            }
    }

    /** 权限被拒。 */
    data class Permission(
        val which: PermissionKind,
        override val cause: Throwable? = null,
    ) : AppError {
        override val userMessage: String
            get() = when (which) {
                PermissionKind.NOTIFICATIONS -> "未授予通知权限，提醒将无法送达"
                PermissionKind.EXACT_ALARM -> "未授予精确闹钟权限，提醒将降级为近似时间"
            }
    }

    /** 存储读写失败（Room / 文件）。 */
    data class Storage(override val cause: Throwable?) : AppError {
        override val userMessage: String = "本地数据读写失败"
    }

    /** 数据缺失（无当前学期 / 无课表）。 */
    data class NotFound(
        val what: String,
        override val cause: Throwable? = null,
    ) : AppError {
        override val userMessage: String = "未找到$what"
    }

    /** 兜底：未分类异常。 */
    data class Unknown(override val cause: Throwable?) : AppError {
        override val userMessage: String = "发生未知错误"
    }
}

enum class AuthReason {
    BAD_CREDENTIALS,
    NEEDS_PHONE_BINDING,
    SMS_CODE_FAILED,
    SESSION_EXPIRED,
}

enum class ParseStage {
    WEEK_EXPRESSION,
    TABLE_STRUCTURE,
    FIELD_MISSING,
    JSON_SCHEMA,
}

enum class PermissionKind {
    NOTIFICATIONS,
    EXACT_ALARM,
}
