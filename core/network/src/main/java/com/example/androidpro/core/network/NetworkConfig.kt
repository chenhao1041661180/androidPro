package com.example.androidpro.core.network

import okhttp3.Interceptor
import java.util.concurrent.TimeUnit

fun interface TokenProvider {
    fun token(): String?
}

/**
 * 全局 HTTP 错误监听：非 2xx（如 400/401/403/404/500/502）统一回调到这里，
 * 用于做跨页面的统一副作用（典型：401 全局踢登录、5xx 埋点上报），
 * 业务层就不必在每个接口都写一遍状态码判断。
 *
 * 注意：这里只做“副作用回调”，不会替换掉每个请求自己返回的 ApiResult.Failure，
 * 调用方依然能拿到完整的 NetworkError（含 httpCode / responseBody）。
 */
fun interface HttpErrorListener {
    /** @param code HTTP 状态码，如 404；@param body 错误响应体（最多 8192 字符），可能为空 */
    fun onHttpError(code: Int, body: String?)
}

data class NetworkConfig(
    val baseUrl: String,
    val tokenProvider: TokenProvider = TokenProvider { null },
    val commonHeaders: () -> Map<String, String> = { emptyMap() },
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
    val writeTimeoutSeconds: Long = 30,
    val debugLogging: Boolean = false,
    val interceptors: List<Interceptor> = emptyList(),
    val onHttpError: HttpErrorListener? = null,
) {
    init {
        require(baseUrl.endsWith('/')) { "baseUrl 必须以 / 结尾" }
        require(connectTimeoutSeconds > 0 && readTimeoutSeconds > 0 && writeTimeoutSeconds > 0)
    }

    internal fun applyTimeouts(builder: okhttp3.OkHttpClient.Builder) = builder
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
}
