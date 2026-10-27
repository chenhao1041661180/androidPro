package com.example.androidpro.core.network

import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把「 会抛异常的 挂起调用」（典型是 Retrofit 的 suspend 接口方法）统一包成 [ApiResult]，
 * 使其与 [SimpleHttp] 的返回形态完全一致：成功 = ApiResult.Success，失败 = ApiResult.Failure(NetworkError)。
 *
 * 这样无论走 OkHttp([SimpleHttp]) 还是 Retrofit([NetworkClient.service])，上层都只用同一套 `when` 处理。
 *
 * 取消保证（与 SimpleHttp 一致）：[CancellationException] 一律原样向上抛，**绝不吞掉**。
 * 否则“界面销毁 → 协程取消”在 Retrofit 路径上会被当成普通失败，协程不会真正结束。
 */
suspend inline fun <T> safeApi(crossinline block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block(), 200, emptyMap())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    // 从 HttpException 取回错误体，补 齐为与另外两条路径一致的信息，并触发全局监听
    val body = runCatching { e.response()?.errorBody()?.string()?.take(8_192) }.getOrNull()
    NetworkClient.httpErrorListener?.onHttpError(e.code(), body)
    ApiResult.Failure(NetworkError(NetworkError.Kind.HTTP, "HTTP ${e.code()}", httpCode = e.code(), responseBody = body, cause = e))
} catch (e: SocketTimeoutException) {
    ApiResult.Failure(NetworkError(NetworkError.Kind.TIMEOUT, "请求超时", cause = e))
} catch (e: UnknownHostException) {
    ApiResult.Failure(NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = e))
} catch (e: IOException) {
    ApiResult.Failure(NetworkError(NetworkError.Kind.IO, e.message ?: "网络读写失败", cause = e))
} catch (e: JsonParseException) {
    ApiResult.Failure(NetworkError(NetworkError.Kind.SERIALIZATION, "数据解析失败", cause = e))
} catch (e: Throwable) {
    ApiResult.Failure(NetworkError(NetworkError.Kind.UNKNOWN, e.message ?: "未知错误", cause = e))
}
