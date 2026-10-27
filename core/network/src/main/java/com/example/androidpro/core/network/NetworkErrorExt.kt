package com.example.androidpro.core.network

/**
 * NetworkError 的语义扩展：把 400/401/403/404/5xx 这类状态码判断收敛成可读的属性，
 * 业务层不用再到处写 `httpCode == 401` 这种魔法数字。
 */

/** 401：未授权，通常是登录态失效 */
val NetworkError.isUnauthorized: Boolean get() = httpCode == 401

/** 403：无权限访问 */
val NetworkError.isForbidden: Boolean get() = httpCode == 403

/** 404：资源不存在 */
val NetworkError.isNotFound: Boolean get() = httpCode == 404

/** 4xx：客户端错误（401/403/404/422 等） */
val NetworkError.isClientError: Boolean get() = httpCode?.let { it in 400..499 } ?: false

/** 5xx：服务端错误（500/502/503 等） */
val NetworkError.isServerError: Boolean get() = httpCode?.let { it in 500..599 } ?: false

/**
 * 面向用户的统一文案。
 *
 * 优先按“错误种类 + 状态码”给出可读提示，避免把英文原始 message（如 "HTTP 502"）直接抛给用户。
 * 各页面若需自定义，仍可基于 [isUnauthorized] / [isServerError] 等自行分支。
 */
fun NetworkError.userMessage(): String = when (kind) {
    NetworkError.Kind.TIMEOUT -> "请求超时，请稍后重试"
    NetworkError.Kind.CONNECTION -> "网络不可用，请检查网络连接"
    NetworkError.Kind.IO -> "网络异常，请稍后重试"
    NetworkError.Kind.SERIALIZATION -> "数据解析失败"
    NetworkError.Kind.HTTP -> when {
        isUnauthorized -> "登录已失效，请重新登录"
        isForbidden -> "没有访问权限"
        isNotFound -> "请求的资源不存在"
        isServerError -> "服务繁忙，请稍后重试"
        isClientError -> "请求有误，请检查后重试"
        else -> message
    }
    NetworkError.Kind.UNKNOWN -> message
}
