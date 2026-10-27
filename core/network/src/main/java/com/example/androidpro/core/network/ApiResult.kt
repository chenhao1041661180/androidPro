package com.example.androidpro.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T, val code: Int, val headers: Map<String, List<String>>) : ApiResult<T>
    data class Failure(val error: NetworkError) : ApiResult<Nothing>
}

data class NetworkError(
    val kind: Kind,
    val message: String,
    val httpCode: Int? = null,
    val responseBody: String? = null,
    val cause: Throwable? = null,
) {
    enum class Kind { HTTP, TIMEOUT, CONNECTION, SERIALIZATION, IO, UNKNOWN }
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data), code, headers)
    is ApiResult.Failure -> this
}
