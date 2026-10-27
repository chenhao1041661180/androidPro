package com.example.androidpro.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 把 OkHttp 的回调式 [Call] 桥接成可取消的挂起函数。
 *
 * 取消保证（本项目网络层的核心契约）：
 * - 使用 [suspendCancellableCoroutine]，并在 [kotlinx.coroutines.CancellableContinuation.invokeOnCancellation]
 *   中调用 [Call.cancel]，因此当调用方所在的协程作用域（lifecycleScope / viewModelScope）被取消时，
 *   这里的挂起会立刻结束，并且**底层的 OkHttp 连接会被真正断开**（不仅是协程停住）。
 * - 也就是说：Activity/Fragment/ViewModel 一旦销毁 → 作用域取消 → 这里的调用取消 → 网络请求同时终止。
 *   不会出现“界面没了，请求还在飞、协程还挂着”的情况。
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) response.close() else continuation.resume(response)
        }
    })
}
