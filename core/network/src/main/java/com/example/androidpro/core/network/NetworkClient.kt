package com.example.androidpro.core.network

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * NetworkClient —— 网络请求库的统一入口（core:network 的门面）。
 *
 * 使用前必须在 Application.onCreate 初始化一次：
 *     NetworkClient.initialize(NetworkConfig(baseUrl = "https://api.example.com/"))  // baseUrl 必须以 / 结尾
 *
 * 之后三处能力都从这里取：
 * - [NetworkClient.http]    -> SimpleHttp（OkHttp 封装，统一返回 ApiResult）
 * - [NetworkClient.service] -> 创建 Retrofit 服务接口（声明 ApiResult<T> 时由 CallAdapter 自动统一返回形态）
 * - [NetworkClient.retrofit]-> 底层 Retrofit 实例（一般用不到）
 *
 * 取消保证：本类不持有长期作用域，请求绑定在调用方协程的作用域上。
 * 用 lifecycleScope → 界面销毁即取消；用 viewModelScope → 扛过旋转、VM 清除才取消。
 */
object NetworkClient {
    @Volatile
    private var holder: Components? = null

    /**
     * 全局 HTTP 错误监听。内部可见，供 safeApi 这类“不在构造链上”的路径复用，
     * 保证三条请求路线（SimpleHttp / Retrofit 统一 / Retrofit 裸+safeApi）行为一致。
     */
    @Volatile
    var httpErrorListener: HttpErrorListener? = null
        private set

    val serviceCache = ConcurrentHashMap<KClass<*>, Any>()

    val http: SimpleHttp
        get() = components().http

    val retrofit: Retrofit
        get() = components().retrofit

    fun initialize(config: NetworkConfig) {
        synchronized(this) {
            serviceCache.clear()
            httpErrorListener = config.onHttpError
            holder = build(config)
        }
    }

    inline fun <reified T : Any> service(): T {
        val key = T::class
        serviceCache[key]?.let { return it as T }
        synchronized(serviceCache) {
            serviceCache[key]?.let { return it as T }
            return retrofit.create(T::class.java).also { serviceCache[key] = it }
        }
    }

    private fun components(): Components = checkNotNull(holder) {
        "请先在 Application.onCreate 中调用 NetworkClient.initialize"
    }

    private fun build(config: NetworkConfig): Components {
        val clientBuilder = config.applyTimeouts(OkHttpClient.Builder())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    config.commonHeaders().forEach { (name, value) -> header(name, value) }
                    config.tokenProvider.token()?.takeIf(String::isNotBlank)?.let {
                        header("Authorization", "Bearer $it")
                    }
                }.build()
                chain.proceed(request)
            }

        config.interceptors.forEach(clientBuilder::addInterceptor)
        if (config.debugLogging) {
            clientBuilder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }

        val client = clientBuilder.build()
        val gson = Gson()
        val retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addCallAdapterFactory(ApiResultCallAdapterFactory(config.onHttpError))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return Components(SimpleHttp(config.baseUrl, client, gson, config.onHttpError), retrofit)
    }

    private data class Components(val http: SimpleHttp, val retrofit: Retrofit)
}
