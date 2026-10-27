package com.example.androidpro.core.network

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * SimpleHttp —— 基于 OkHttp 的轻量封装，所有方法都是 suspend 且统一返回 [ApiResult]。
 *
 * 生命周期/取消约定（重要）：
 * 本类不持有任何长期作用域；每一次请求都在“调用方当前协程”内执行，并经由 [OkHttpAwait.await]
 * 桥接到可取消的 OkHttp 调用。因此：
 * - 在 `lifecycleScope.launch { }` 里调用 → Fragment/Activity 销毁即取消（**界面消失即停止**）。
 * - 在 `viewModelScope.launch { }` 里调用 → ViewModel 清除时取消（**扛过旋转**，重建不重发）。
 * - 无论哪种，取消时**协程与底层网络请求一起结束**，不会泄漏连接或残留协程。
 * - 下载/上传的流读写循环也内置了取消检查，大文件也能即时中断。
 */
class SimpleHttp internal constructor(
    baseUrl: String,
    private val client: OkHttpClient,
    private val gson: Gson,
    private val onHttpError: HttpErrorListener? = null,
) {
    private val baseUrl = baseUrl.toHttpUrl()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(request("GET", path, query, headers = headers), typeOf<T>())

    suspend inline fun <reified T> delete(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(request("DELETE", path, query, headers = headers), typeOf<T>())

    suspend inline fun <reified T> deleteJson(
        path: String,
        body: Any,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(request("DELETE", path, body = json(body), headers = headers), typeOf<T>())

    suspend inline fun <reified T> postJson(
        path: String,
        body: Any,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(request("POST", path, body = json(body), headers = headers), typeOf<T>())

    suspend inline fun <reified T> putJson(
        path: String,
        body: Any,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(request("PUT", path, body = json(body), headers = headers), typeOf<T>())

    suspend inline fun <reified T> postForm(
        path: String,
        fields: Map<String, Any?>,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> {
        val form = FormBody.Builder().apply {
            fields.forEach { (name, value) -> if (value != null) add(name, value.toString()) }
        }.build()
        return execute(request("POST", path, body = form, headers = headers), typeOf<T>())
    }

    @RequiresApi(Build.VERSION_CODES.N)
    suspend inline fun <reified T> upload(
        path: String,
        file: File,
        fileField: String = "file",
        fields: Map<String, String> = emptyMap(),
        mimeType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        noinline onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ApiResult<T> {
        val job = coroutineContext[Job]
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach(::addFormDataPart)
            // 把“协程是否还活着”传进 ProgressRequestBody，上传过程可随界面销毁即时中断
            addFormDataPart(
                fileField, file.name,
                ProgressRequestBody(file, mimeType.toMediaType(), onProgress) { job?.isActive ?: true },
            )
        }.build()
        return execute(request("POST", path, body = multipart, headers = headers), typeOf<T>())
    }

    suspend fun download(
        path: String,
        destination: File,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request("GET", path, query, headers = headers)).await().use { response ->
                if (!response.isSuccessful) return@withContext response.failure()
                val body = response.body
                val total = body.contentLength()
                destination.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            // 每读一块先检查协程是否被取消：界面销毁时立刻停止下载，不把流读完
                            (coroutineContext[Job])?.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            onProgress(written, total)
                        }
                    }
                }
                ApiResult.Success(destination, response.code, response.headers.toMultimap())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(error.toNetworkError())
        }
    }

    @PublishedApi
    internal fun request(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): Request {
        val resolved = baseUrl.resolve(path) ?: error("无法解析请求路径: $path")
        val url = resolved.newBuilder().apply {
            query.forEach { (name, value) ->
                when (value) {
                    null -> Unit
                    is Iterable<*> -> value.filterNotNull().forEach { addQueryParameter(name, it.toString()) }
                    else -> addQueryParameter(name, value.toString())
                }
            }
        }.build()
        return Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            method(method, body)
        }.build()
    }

    @PublishedApi
    internal fun json(value: Any): RequestBody = gson.toJson(value).toRequestBody(jsonType)

    @PublishedApi
    internal suspend fun <T> execute(request: Request, type: Type): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext response.failure()
                val text = response.body.string()
                @Suppress("UNCHECKED_CAST")
                val value = if (type == Unit::class.java) Unit as T else gson.fromJson<T>(text, type)
                ApiResult.Success(value, response.code, response.headers.toMultimap())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ApiResult.Failure(error.toNetworkError())
        }
    }

    @PublishedApi
    internal inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type

    private fun okhttp3.Response.failure(): ApiResult.Failure {
        // 错误体只能读一次，先取出再同时交给全局监听与 Failure，避免重复消费
        val errorBody = runCatching { body.string() }.getOrDefault("").take(8_192)
        onHttpError?.onHttpError(code, errorBody)
        return ApiResult.Failure(
            NetworkError(
                kind = NetworkError.Kind.HTTP,
                message = "HTTP $code",
                httpCode = code,
                responseBody = errorBody,
            ),
        )
    }

    private fun Throwable.toNetworkError(): NetworkError = when (this) {
        is SocketTimeoutException -> NetworkError(NetworkError.Kind.TIMEOUT, "请求超时", cause = this)
        is UnknownHostException -> NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = this)
        is IOException -> NetworkError(NetworkError.Kind.IO, message ?: "网络读写失败", cause = this)
        is com.google.gson.JsonParseException -> NetworkError(NetworkError.Kind.SERIALIZATION, "数据解析失败", cause = this)
        else -> NetworkError(NetworkError.Kind.UNKNOWN, message ?: "未知错误", cause = this)
    }
}
