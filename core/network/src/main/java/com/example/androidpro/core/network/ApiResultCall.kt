package com.example.androidpro.core.network

import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Type
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把底层 Retrofit 的 Call<T> 包装成 Call<ApiResult<T>>，使 Retrofit 接口方法可以直接声明返回 ApiResult<T>。
 *
 * 转换规则（与 SimpleHttp 一致）：
 * - 成功 -> ApiResult.Success；非 2xx -> ApiResult.Failure(HTTP)。
 * - 超时 -> TIMEOUT；无法连接/解析主机 -> CONNECTION；读写失败 -> IO；解析失败 -> SERIALIZATION。
 *
 * 取消保证（与 SimpleHttp 一致）：Retrofit/OkHttp 在协程取消时是“以 IOException("Canceled") 回调 onFailure”
 * 而不是抛 CancellationException，这里用 delegate.isCanceled() 识别并转成 CancellationException 原样上抛，
 * 保证“界面销毁 -> 协程取消”在 Retrofit 路径上同样生效，不会被当成普通失败。
 */
internal class ApiResultCall<T : Any>(
    private val delegate: Call<T>,
    private val innerType: Type,
    private val onHttpError: HttpErrorListener? = null,
) : Call<ApiResult<T>> {

    override fun enqueue(callback: Callback<ApiResult<T>>) {
        delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                callback.onResponse(this@ApiResultCall, Response.success(toApiResult(response)))
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                if (t is CancellationException || delegate.isCanceled()) {
                    // 把取消转成 CancellationException，让协程真正结束，而不是被当成普通失败
                    callback.onFailure(this@ApiResultCall, CancellationException("请求已取消", t))
                } else {
                    callback.onResponse(this@ApiResultCall, Response.success(toApiResult(t)))
                }
            }
        })
    }

    override fun execute(): Response<ApiResult<T>> =
        Response.success(toApiResult(runCatching { delegate.execute() }))

    override fun clone(): Call<ApiResult<T>> = ApiResultCall(delegate.clone(), innerType, onHttpError)
    override fun request(): Request = delegate.request()
    override fun timeout(): Timeout = delegate.timeout()
    override fun isExecuted(): Boolean = delegate.isExecuted()
    override fun isCanceled(): Boolean = delegate.isCanceled()
    override fun cancel(): Unit = delegate.cancel()

    private fun toApiResult(response: Response<T>): ApiResult<T> {
        return if (response.isSuccessful) {
            val raw = response.body()
            // 空 body 且声明为 ApiResult<Unit> 时给 Unit，避免 null as Unit 抛异常
            val data = if (raw == null && innerType == Unit::class.java) Unit else raw
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(data as T, response.code(), response.headers().toMultimap())
        } else {
            // 错误体只能读一次，先取出再同时交给全局监听与 Failure，避免重复消费
            val body = runCatching { response.errorBody()?.string()?.take(8_192) }.getOrNull()
            onHttpError?.onHttpError(response.code(), body)
            ApiResult.Failure(
                NetworkError(NetworkError.Kind.HTTP, "HTTP ${response.code()}", httpCode = response.code(), responseBody = body),
            )
        }
    }

    private fun toApiResult(result: Result<Response<T>>): ApiResult<T> =
        result.fold(onSuccess = { toApiResult(it) }, onFailure = { toApiResult(it) })

    private fun toApiResult(t: Throwable): ApiResult<T> = when (t) {
        is CancellationException -> throw t
        is ConnectException -> ApiResult.Failure(NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = t))
        is UnknownHostException -> ApiResult.Failure(NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = t))
        is SocketTimeoutException -> ApiResult.Failure(NetworkError(NetworkError.Kind.TIMEOUT, "请求超时", cause = t))
        is IOException -> ApiResult.Failure(NetworkError(NetworkError.Kind.IO, t.message ?: "网络读写失败", cause = t))
        is JsonParseException -> ApiResult.Failure(NetworkError(NetworkError.Kind.SERIALIZATION, "数据解析失败", cause = t))
        else -> ApiResult.Failure(NetworkError(NetworkError.Kind.UNKNOWN, t.message ?: "未知错误", cause = t))
    }
}
