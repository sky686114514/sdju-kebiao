package com.kebiao.app.core

/**
 * 统一的"成功 / 失败"封装。
 *
 * 仓库层与导入层以它作为返回类型，**不向上抛原始异常、也不吞掉异常**
 * （ARCHITECTURE.md 第 12 节分层映射规则）。
 *
 * 这是 `Result<T>` 的项目内替代：`Result` 的 `Failure` 只带 `Throwable`，
 * 而我们需要携带分类化的 [AppError] 供 UI 直接映射文案。
 */
sealed interface AppResult<out T> {

    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>

    companion object {
        fun <T> success(value: T): AppResult<T> = Success(value)

        fun <T> failure(error: AppError): AppResult<T> = Failure(error)

        fun <T> failure(cause: Throwable): AppResult<T> = Failure(cause.toAppError())
    }
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error

/** 取成功值；失败时抛 [IllegalStateException]，**仅用于断言性场景，不用于业务控制流**。 */
fun <T> AppResult<T>.getOrThrow(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw IllegalStateException("期望成功但得到失败: ${error.userMessage}", error.cause)
}

/**
 * 底层异常 -> [AppError] 的统一翻译入口。
 *
 * 只在**数据层边界**调用一次；越界异常（如修复中的 SQLException）由这里的子类判定兜住，
 * 而不是在业务代码里到处 `try/catch`。
 */
fun Throwable.toAppError(): AppError = when (this) {
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is javax.net.ssl.SSLException,
    -> AppError.Network(this)

    is SecurityException -> AppError.Unknown(this)

    else -> AppError.Unknown(this)
}
