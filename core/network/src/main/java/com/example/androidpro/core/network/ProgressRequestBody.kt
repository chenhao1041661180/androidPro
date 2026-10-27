package com.example.androidpro.core.network

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

/**
 * 带进度回调的请求体。
 *
 * [isActive] 用于在写文件的过程中响应协程取消：当调用方所在的
 * lifecycleScope / viewModelScope 被取消（Activity/Fragment/ViewModel 销毁）时，
 * [isActive] 会变 false，[writeTo] 立刻抛出 CancellationException，
 * 让“界面消失 → 上传中断 + 协程结束”同时发生，而不是把整文件写完才停。
 */
@PublishedApi
internal class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit,
    private val isActive: () -> Boolean = { true },
) : RequestBody() {
    override fun contentType(): MediaType? = contentType
    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                // 每写一块先检查协程是否被取消：界面销毁时立即停止，避免多余的上传流量
                if (!isActive()) throw CancellationException("上传已取消：所在界面已销毁")
                val count = input.read(buffer)
                if (count < 0) break
                sink.write(buffer, 0, count)
                written += count
                onProgress(written, total)
            }
        }
    }
}
