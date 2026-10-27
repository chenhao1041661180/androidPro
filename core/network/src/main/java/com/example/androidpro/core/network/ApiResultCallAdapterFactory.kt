package com.example.androidpro.core.network

import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 让 Retrofit 接口方法可以直接声明返回 ApiResult<T>：
 *
 *     @GET("users/{id}")
 *     suspend fun getUser(@Path("id") id: String): ApiResult<User>
 *
 * 由 [ApiResultCall] 负责把底层响应/异常统一成 ApiResult，与 SimpleHttp 的返回形态完全一致。
 * 非 ApiResult<T> 的返回类型（如裸 T、Response<T>、Call<T>）本工厂返回 null，交由 Retrofit 默认适配器处理，
 * 因此“原始 Retrofit 用法”与“统一 ApiResult 用法”可并存，互不破坏。
 *
 * [onHttpError] 来自 NetworkConfig，用于把 400/401/500 等非 2xx 统一回调到全局监听。
 */
internal class ApiResultCallAdapterFactory(
    private val onHttpError: HttpErrorListener? = null,
) : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (CallAdapter.Factory.getRawType(returnType) != ApiResult::class.java) return null
        require(returnType is ParameterizedType) { "ApiResult 必须带泛型参数，例如 ApiResult<User>" }
        val innerType = returnType.actualTypeArguments[0]
        return ApiResultCallAdapter(innerType, onHttpError)
    }
}

private class ApiResultCallAdapter(
    private val innerType: Type,
    private val onHttpError: HttpErrorListener?,
) : CallAdapter<Any, Call<ApiResult<Any>>> {
    // responseType 告知 Retrofit 把响应体反序列化成哪个类型（内层 T）
    override fun responseType(): Type = innerType

    override fun adapt(call: Call<Any>): Call<ApiResult<Any>> = ApiResultCall(call, innerType, onHttpError)
}
