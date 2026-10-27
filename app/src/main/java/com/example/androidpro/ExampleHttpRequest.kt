package com.example.androidpro

import com.example.androidpro.core.network.ApiResult
import com.example.androidpro.core.network.NetworkError
import com.example.androidpro.core.network.NetworkClient
import com.example.androidpro.core.network.safeApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.zip
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * ExampleHttpRequest —— 本项目网络请求在协程中的用法示例（OkHttp / Retrofit 两个版本）
 * =============================================================================================
 * 阅读前先知道三件事：
 *
 * 1) 入口（都在 [NetworkClient]）：
 *    - NetworkClient.http            -> SimpleHttp（OkHttp 封装，本项目主用）
 *    - NetworkClient.service<XApi>() -> Retrofit 声明式接口
 *    - NetworkClient.retrofit        -> 底层 Retrofit 实例
 *    三者共享同一个 OkHttpClient（已包含 token 拦截器、公共头、超时）。
 *    使用前必须在 Application.onCreate 调一次：
 *        NetworkClient.initialize(NetworkConfig(baseUrl = "https://api.example.com/"))  // baseUrl 必须以 / 结尾
 *
 * 2) 两条技术路线的“原始”返回形态不同，但可通过 safeApi 统一为同一种：
 *    - SimpleHttp（OkHttp）：所有方法都是 suspend，天然返回 ApiResult<T>。
 *      成功 = ApiResult.Success(data, code, headers)；失败 = ApiResult.Failure(NetworkError)，
 *      **不会抛异常**（协程取消除外，仍是 CancellationException 向上传）。
 *    - Retrofit：suspend 方法原始返回裸类型 T，失败会抛 HttpException / IOException 等异常，
 *      **不包 ApiResult**。统一方式：用 safeApi { api.xxx() } 包一层
 *      （safeApi 已在 core:network 提供，错误映射与 SimpleHttp 完全一致，含 SERIALIZATION，
 *      且会原样抛出 CancellationException，取消保证同样生效）。
 *    => 统一后，两条路径上层都只用同一套 `when (ApiResult)` 处理，无需区分。
 *    - 进一步“类型层面”统一：给 Retrofit 注册 ApiResultCallAdapterFactory 后，接口方法可直接声明
 *      返回 ApiResult<T>（见下方 UserApiUnified），连 safeApi { } 都不用写，详见“统一风格”小节。
 *
 * 3) 线程：两个方案的网络请求内部都已在 IO 线程执行，调用方直接
 *    viewModelScope.launch { ... } / lifecycleScope.launch { ... } 即可，**不要**再手动 withContext(Dispatchers.IO)。
 *
 * 4) 取消保证（必读）：本项目的网络请求**跟着调用方协程的作用域走**，不会出现
 *    “Activity/Fragment/ViewModel 都没了，请求还在继续、协程还挂着”的情况：
 *    - 底层所有 OkHttp 调用都经过 [OkHttpAwait.await]（suspendCancellableCoroutine +
 *      invokeOnCancellation { call.cancel() }），作用域一取消，协程和底层 socket 同时断掉。
 *    - 界面销毁想立刻停 → 用 Fragment/Activity 的 lifecycleScope（销毁即取消）。
 *    - 想扛过屏幕旋转、重建不重发 → 用 viewModelScope（VM 清除才取消）。
 *    - 下载/上传的流读写循环也内置了取消检查，大文件也能即时中断。
 *    - 口诀：在哪里 launch，就由哪里管生死；不要自己 new CoroutineScope() 也不取消，更不要用 GlobalScope。
 * =============================================================================================
 */

// ============================================================
// 示例数据模型（file-private，不会污染其它代码）
// ============================================================
private data class User(val id: String, val name: String, val age: Int)
private data class CreateUserReq(val name: String, val age: Int)
private data class UpdateUserReq(val name: String?)
private data class Token(val accessToken: String)
private data class UploadResult(val url: String)
private data class Item(val id: String, val title: String)

// ============================================================
// 示例 Retrofit 接口（声明式，注解方式）
// ============================================================
private interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): User

    @POST("users")
    suspend fun createUser(@Body body: CreateUserReq): User

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun login(@Field("code") code: String): Token

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: UpdateUserReq): User

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String)
}

// ============================================================
// 统一风格：接口方法直接声明返回 ApiResult<T>（类型层面统一，无需再用 safeApi 包裹）
// 前提：NetworkClient 已注册 ApiResultCallAdapterFactory（core:network 内部已注册）。
// ============================================================
private interface UserApiUnified {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): ApiResult<User>

    @POST("users")
    suspend fun createUser(@Body body: CreateUserReq): ApiResult<User>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String): ApiResult<Unit>
}

/** 统一风格调用：与 SimpleHttp 完全同形态，上层只用一套 when 处理 */
private suspend fun retrofitUnified() {
    val api = NetworkClient.service<UserApiUnified>()
    when (val r = api.getUser("1")) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
    // 其它方法同理：api.createUser(...)、api.deleteUser(...) 都直接返回 ApiResult
}

// ============================================================
// 通用：把 Retrofit 调用包成 ApiResult（与 SimpleHttp 同形态）
// 说明：safeApi 已提升到 core:network（同包可见），统一负责错误映射，
//      包含 SERIALIZATION（Gson 在 core:network 内可用）且会原样抛出 CancellationException，
//      保证“界面销毁 → 协程取消”在 Retrofit 路径上同样生效。
// ============================================================

// ============================================================
// 一、OkHttp 版（SimpleHttp，统一返回 ApiResult）—— 覆盖所有请求方式
// 注意：SimpleHttp 没有“裸” post/put，必须指明 json / form / multipart。
// ============================================================

/** GET：路径参数 + 可选 query / headers */
private suspend fun okhttpGet() {
    when (val r = NetworkClient.http.get<User>("users/1", query = mapOf("include" to "profile"))) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** POST（JSON 体） */
private suspend fun okhttpPostJson() {
    when (val r = NetworkClient.http.postJson<User>("users", body = CreateUserReq("Rocky", 30))) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** POST（表单） */
private suspend fun okhttpPostForm() {
    when (val r = NetworkClient.http.postForm<Token>("oauth/token", fields = mapOf("code" to "abc", "grant_type" to "authorization_code"))) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** PUT（JSON 体） */
private suspend fun okhttpPutJson() {
    when (val r = NetworkClient.http.putJson<User>("users/1", body = UpdateUserReq(name = "Rocky"))) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** DELETE：没有 body */
private suspend fun okhttpDelete() {
    when (val r = NetworkClient.http.delete<Unit>("users/1")) {
        is ApiResult.Success -> handle(Unit, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** DELETE（带 JSON body） */
private suspend fun okhttpDeleteJson() {
    when (val r = NetworkClient.http.deleteJson<Unit>("users/1", body = mapOf("reason" to "duplicate"))) {
        is ApiResult.Success -> handle(Unit, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** 上传（multipart）。注意：SimpleHttp.upload 标注了 @RequiresApi(Build.VERSION_CODES.N)，调用方需确保 API 24+ 或做兼容。 */
private suspend fun okhttpUpload() {
    val file = File("/sdcard/avatar.png") // 替换为真实文件
    when (
        val r = NetworkClient.http.upload<UploadResult>(
            path = "files",
            file = file,
            fileField = "file",
            fields = mapOf("type" to "avatar"),
            onProgress = { current, total -> println("上传进度 $current / $total") },
        )
    ) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** 下载（直接写到文件，返回 ApiResult<File>） */
private suspend fun okhttpDownload() {
    val dest = File("/sdcard/report.pdf") // 替换为真实目标路径
    when (val r = NetworkClient.http.download("files/report", destination = dest, onProgress = { c, t -> println("下载 $c/$t") })) {
        is ApiResult.Success -> println("已保存到 ${r.data.absolutePath}")
        is ApiResult.Failure -> handleError(r.error)
    }
}

// ============================================================
// 二、Retrofit 版（声明式接口）—— 同样覆盖所有请求方式
// 特点：默认返回裸类型、失败抛异常；下面演示“裸 try/catch”与“safeApi 包裹”两种写法。
// ============================================================

/** GET：裸调用 + try/catch */
private suspend fun retrofitGetRaw() {
    val api = NetworkClient.service<UserApi>()
    try {
        val user = api.getUser("1") // 成功直接拿到 User
        handle(user, 200)
    } catch (e: HttpException) {
        handleError(NetworkError(NetworkError.Kind.HTTP, "HTTP ${e.code()}", httpCode = e.code(), cause = e))
    } catch (e: SocketTimeoutException) {
        handleError(NetworkError(NetworkError.Kind.TIMEOUT, "请求超时", cause = e))
    } catch (e: IOException) {
        handleError(NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = e))
    }
}

/** POST（JSON）：用 safeApi 统一包成 ApiResult */
private suspend fun retrofitPostJsonSafe() {
    val api = NetworkClient.service<UserApi>()
    when (val r = safeApi { api.createUser(CreateUserReq("Rocky", 30)) }) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** POST（表单）：safeApi 包裹 */
private suspend fun retrofitPostFormSafe() {
    val api = NetworkClient.service<UserApi>()
    when (val r = safeApi { api.login("abc") }) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** PUT（JSON）：safeApi 包裹 */
private suspend fun retrofitPutJsonSafe() {
    val api = NetworkClient.service<UserApi>()
    when (val r = safeApi { api.updateUser("1", UpdateUserReq(name = "Rocky")) }) {
        is ApiResult.Success -> handle(r.data, r.code)
        is ApiResult.Failure -> handleError(r.error)
    }
}

/** DELETE：裸调用（无返回值），异常由 try 兜 */
private suspend fun retrofitDeleteRaw() {
    val api = NetworkClient.service<UserApi>()
    try {
        api.deleteUser("1")
        println("删除成功")
    } catch (e: HttpException) {
        handleError(NetworkError(NetworkError.Kind.HTTP, "HTTP ${e.code()}", httpCode = e.code(), cause = e))
    } catch (e: IOException) {
        handleError(NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = e))
    }
}

// ============================================================
// 三、多个请求“同时发生”（并发、互相独立）
// 核心：在 coroutineScope 内用 async 同时发起，最后 awaitAll() 等全部完成。
// 注意：
//   - SimpleHttp 已用 ApiResult 包裹，单个失败不会取消其它请求，可直接 awaitAll 后逐个 when。
//   - Retrofit 会抛异常：若希望“一个失败不影响其它”，用 supervisorScope（见下）。
// ============================================================

/** 方式 A：全部并发，等全部完成（最常用） */
private suspend fun concurrentAllAtOnce() = coroutineScope {
    val deferredA = async { NetworkClient.http.get<User>("users/1") }
    val deferredB = async { NetworkClient.http.get<User>("users/2") }
    val deferredC = async { NetworkClient.http.get<List<User>>("teams") }

    // 三个结果类型已知，直接 await 后逐个处理（结果顺序与发起顺序一致）
    when (val a = deferredA.await()) {
        is ApiResult.Success -> println("A 成功: ${a.data}")
        is ApiResult.Failure -> println("A 失败: ${a.error.message}")
    }
    when (val b = deferredB.await()) {
        is ApiResult.Success -> println("B 成功: ${b.data}")
        is ApiResult.Failure -> println("B 失败: ${b.error.message}")
    }
    when (val c = deferredC.await()) {
        is ApiResult.Success -> println("C 成功: ${c.data}")
        is ApiResult.Failure -> println("C 失败: ${c.error.message}")
    }
}

/** 方式 B：并发但“任一失败不连坐”（适合 Retrofit 或要求隔离的场景） */
private suspend fun concurrentIndependentSafe() = supervisorScope {
    val a = async { NetworkClient.http.get<User>("users/1") }
    val b = async { NetworkClient.http.get<User>("users/2") }
    // 即便 a 失败（ApiResult.Failure），b 仍正常执行；await 时 SimpleHttp 不会抛，直接拿到 Failure
    val ra = a.await()
    val rb = b.await()
    println("a=$ra, b=$rb")
}

// ============================================================
// 四、请求“相互关联”（下一个需要上一个的返回）—— 顺序调用即可
// 同一协程内天然串行：写一行等一行，结果直接当变量用。
// ============================================================

/** 用 SimpleHttp（ApiResult）：用 when 串起两个请求 */
private suspend fun sequentialDependentOkHttp() {
    val login = NetworkClient.http.postForm<Token>("oauth/token", fields = mapOf("code" to "abc"))
    when (login) {
        is ApiResult.Success -> {
            val token = login.data.accessToken
            // 用上一个返回里的 token 调下一个接口
            when (val profile = NetworkClient.http.get<User>("users/me", headers = mapOf("Authorization" to "Bearer $token"))) {
                is ApiResult.Success -> handle(profile.data, profile.code)
                is ApiResult.Failure -> handleError(profile.error)
            }
        }
        is ApiResult.Failure -> handleError(login.error)
    }
}

/** 用 Retrofit（裸类型）：直接顺序写，异常由外层 try 兜 */
private suspend fun sequentialDependentRetrofit() {
    val api = NetworkClient.service<UserApi>()
    try {
        val token = api.login("abc").accessToken   // 第 1 个请求
        val me = api.getUser(token)                 // 第 2 个请求，依赖第 1 个的返回
        handle(me, 200)
    } catch (e: HttpException) {
        handleError(NetworkError(NetworkError.Kind.HTTP, "HTTP ${e.code()}", httpCode = e.code(), cause = e))
    } catch (e: IOException) {
        handleError(NetworkError(NetworkError.Kind.CONNECTION, "网络不可用", cause = e))
    }
}

// ============================================================
// 五、混合：先并发拉几个，再用其中一个结果发起依赖请求
// ============================================================
private suspend fun mixedConcurrentThenDependent() = coroutineScope {
    // 第 1 步：并发拉 A、B
    val da = async { NetworkClient.http.get<User>("a") }
    val db = async { NetworkClient.http.get<User>("b") }
    val a = da.await()
    val b = db.await()

    // 第 2 步：用 A 的结果（若存在）发起依赖请求 C
    if (a is ApiResult.Success) {
        when (val c = NetworkClient.http.get<User>("c/${a.data.id}")) {
            is ApiResult.Success -> handle(c.data, c.code)
            is ApiResult.Failure -> handleError(c.error)
        }
    }
    println("b=$b")
}

// ============================================================
// 六、并发 + 各自独立渲染（谁先回来谁先显示，互相不影响）
// =============================================================================================
// 场景：首页同时请求 A（轮播）、B（列表）、C（公告）三块“互不相关”的数据，
//       哪块先返回，哪块就先渲染；某块失败只影响自己，不阻塞其它块。
//
// 与“第三章（等全部完成）”的核心区别：
//   - 第三章用 async + awaitAll/await：要等“全部”拿到才统一处理 —— 慢的那个会拖住快的。
//   - 本章用 launch（每个请求一个独立协程）+ 独立的 UI 状态（StateFlow）：
//     每个请求完成就立刻更新“自己那块”的状态，UI 各自订阅，互不打断。快的先到先显示。
//
// 三个要点（缺一不可）：
//   1) 每块数据配一个“独立”的 StateFlow（_aState / _bState / _cState）。
//      不要用一个“大 UiState 包全部”，否则快的也要等慢的才能刷新整页。
//   2) 每个请求单独 launch（viewModelScope 是 SupervisorJob，单个失败默认不连坐其它）。
//   3) 在 viewModelScope（主线程）里更新 StateFlow，UI 端 collect 自动切主线程渲染。
// =============================================================================================

/** 每块 UI 状态的通用密封类（加载中 / 成功 / 失败），各区块共用即可 */
private sealed interface BlockUiState<out T> {
    data object Loading : BlockUiState<Nothing>
    data class Ok<T>(val data: T) : BlockUiState<T>
    data class Err(val message: String) : BlockUiState<Nothing>
}

// ---- 6.1 演示 ViewModel：三块数据各自独立加载（SimpleHttp 版，推荐）----
/*
class HomeViewModel : androidx.lifecycle.ViewModel() {

    // 每块一个独立 StateFlow，互不耦合 —— 这是“快者先渲染”的关键
    private val _bannerState = MutableStateFlow<BlockUiState<List<String>>>(BlockUiState.Loading)
    val bannerState: StateFlow<BlockUiState<List<String>>> = _bannerState

    private val _feedState = MutableStateFlow<BlockUiState<List<User>>>(BlockUiState.Loading)
    val feedState: StateFlow<BlockUiState<List<User>>> = _feedState

    private val _noticeState = MutableStateFlow<BlockUiState<String>>(BlockUiState.Loading)
    val noticeState: StateFlow<BlockUiState<String>> = _noticeState

    /** 一键并发加载全部：三块请求同时“飞”出去，谁先回来谁先改自己的 StateFlow */
    fun loadHomeByBlock() {
        // 块 A：轮播（与下面两块同时发起）
        viewModelScope.launch {
            when (val r = NetworkClient.http.get<List<String>>("home/banner")) {
                is ApiResult.Success -> _bannerState.value = BlockUiState.Ok(r.data)
                is ApiResult.Failure -> _bannerState.value = BlockUiState.Err(r.error.message ?: "轮播加载失败")
            }
        }
        // 块 B：信息流（独立协程，A 卡住/失败都不影响它）
        viewModelScope.launch {
            when (val r = NetworkClient.http.get<List<User>>("home/feed")) {
                is ApiResult.Success -> _feedState.value = BlockUiState.Ok(r.data)
                is ApiResult.Failure -> _feedState.value = BlockUiState.Err(r.error.message ?: "列表加载失败")
            }
        }
        // 块 C：公告（同样独立）
        viewModelScope.launch {
            when (val r = NetworkClient.http.get<String>("home/notice")) {
                is ApiResult.Success -> _noticeState.value = BlockUiState.Ok(r.data)
                is ApiResult.Failure -> _noticeState.value = BlockUiState.Err(r.error.message ?: "公告加载失败")
            }
        }
        // 注意：这里没有任何 await / join。三块各自跑、各自回、各自渲染，天然“先到先显示”。
    }
}
*/

// ---- 6.2 如果用 Retrofit（裸类型、失败抛异常）：每个 launch 内部必须 try/catch ----
/*
class HomeViewModelRetrofit : androidx.lifecycle.ViewModel() {
    private val _feedState = MutableStateFlow<BlockUiState<List<User>>>(BlockUiState.Loading)
    val feedState: StateFlow<BlockUiState<List<User>>> = _feedState

    fun loadIndependent() {
        viewModelScope.launch {                 // 块 A
            try {
                val data = NetworkClient.service<UserApi>().getUsers()   // 抛异常不会连坐兄弟（SupervisorJob）
                _feedState.value = BlockUiState.Ok(data)
            } catch (e: Exception) {
                _feedState.value = BlockUiState.Err(e.message ?: "加载失败")
            }
        }
        viewModelScope.launch {                 // 块 B：即便 A 异常，这里仍独立执行
            try {
                val data = NetworkClient.service<UserApi>().getTeams()
                _teamState.value = BlockUiState.Ok(data)
            } catch (e: Exception) {
                _teamState.value = BlockUiState.Err(e.message ?: "加载失败")
            }
        }
    }
}
// 提醒：虽然 SupervisorJob 保证“一个失败不取消其他”，但 Retrofit 抛出的未捕获异常仍会
//      向上传播成为“未处理协程异常”，在 Android 上可能触发崩溃上报。因此每个 launch 内
//      务必 try/catch 并写入对应 Err 状态。SimpleHttp 因为返回 ApiResult 不抛异常，不会有此问题。
*/

// ---- 6.3 UI 端：各自 collect 自己那块，谁先到谁先显示 ----
/*
// Fragment 中（View 体系）：
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.bannerState.collect { state ->
        when (state) {
            is BlockUiState.Loading -> showBannerSkeleton()    // 这块先到就先渲染，不等其它块
            is BlockUiState.Ok    -> renderBanner(state.data)
            is BlockUiState.Err   -> showBannerError(state.message)
        }
    }
}
// feedState / noticeState 同理，各自 collect 各自渲染，互相完全独立。
//
// Compose 中更简单：
//     val feed by viewModel.feedState.collectAsStateWithLifecycle()
//     when (feed) { is Loading -> ...; is Ok -> LazyColumn(...); is Err -> ... }
*/

// ============================================================
// 七、网络请求与 Flow 结合（响应式写法，覆盖上述全部场景）
// =============================================================================================
// 前面第三~六章是「suspend 直接调用」的写法。本章用 Flow 的响应式写法重写同样的场景，
// 并补充 Flow 独有的杀手锏（防抖搜索、自动重试、组合流）。
//
// suspend 与 Flow 的核心差异：
//   - suspend 函数：调一次、返回一次，是「一次性」结果。
//   - Flow：冷流、可发射多个值，能挂操作符链（map / flatMap / catch / retry / zip / combine），
//           天然适合「数据会变 / 想复用同一套异常与转换」的场景。
//   本项目其实已经在用 Flow：core:list 的 ListRepository 就输出 Flow<PagingData<Item>>。
// =============================================================================================

// ---- 7.0 基础：把“单次网络请求”包成一个冷流 Flow（所有 Flow 玩法的基础）----
// 每次 collect 才真正发请求；失败不抛（转 ApiResult.Failure），便于下游统一 when。
private fun <T> requestFlow(block: suspend () -> ApiResult<T>): Flow<ApiResult<T>> =
    flow { emit(block()) }
        .flowOn(Dispatchers.IO)                          // 保险：确保网络在 IO（调用方已保证则重复无害）
        .retry(2) { it is IOException }                  // 可选：连接类错误自动重试 2 次
        .catch { e ->                                    // 兜底：任何未捕获异常都转 ApiResult.Failure
            emit(ApiResult.Failure(NetworkError(NetworkError.Kind.UNKNOWN, e.message ?: "异常", cause = e)))
        }

// 便捷封装：直接给 path 拉 GET 的 Flow
private inline fun <reified T> getFlow(path: String): Flow<ApiResult<T>> =
    requestFlow { NetworkClient.http.get<T>(path) }

// ---- 7.1 并发、互不相关，且“等全部到齐再组合” → zip（对应第三章 awaitAll）----
// zip：等两个源都发射后，用 transform 组合成一个结果。语义近似 awaitAll，但是响应式的。
private fun loadZip(): Flow<Pair<ApiResult<List<User>>, ApiResult<String>>> =
    getFlow<List<User>>("home/feed")
        .zip(getFlow<String>("home/notice")) { feed, notice -> feed to notice }
// 使用（在 ViewModel / 协程里）：
//     viewModelScope.launch { loadZip().collect { (feed, notice) -> /* 两个都到了才进来一次 */ } }

// ---- 7.2 并发、互不相关，且“谁先到谁先触发下游” → combine（对应第六章“先到先渲染”的整体版）----
// combine：任一源 Flow 发射新值，立刻用“各方最新值”重新组合下游。
// 哪个请求先回来，哪个就先触发一次下游 —— 适合“多区块整体刷新但想响应式”的聚合场景。
private fun homeCombined(): Flow<Triple<ApiResult<List<User>>, ApiResult<String>, ApiResult<List<String>>>> =
    combine(
        getFlow<List<User>>("home/feed"),
        getFlow<String>("home/notice"),
        getFlow<List<String>>("home/banner")
    ) { feed, notice, banner -> Triple(feed, notice, banner) }
// 注：若想“每块独立渲染、互不打断”，第六章的「一请求一 launch + 一块一 StateFlow」更直白；
//     combine 适合“需要整体一起变”的聚合场景（任一块更新都重算整体）。

// ---- 7.3 请求相互关联（下一个需要上一个的返回）→ flatMapConcat（串行 + 可传参）----
// 对应第四章 sequentialDependentOkHttp 的 Flow 写法：上游值到了，再顺序执行下游 Flow。
private fun loginThenProfile(): Flow<ApiResult<User>> =
    getFlow<Token>("oauth/token").flatMapConcat { tokenResult ->
        when (tokenResult) {
            is ApiResult.Success ->
                requestFlow {
                    NetworkClient.http.get<User>(
                        "users/me",
                        headers = mapOf("Authorization" to "Bearer ${tokenResult.data.accessToken}"),
                    )
                }
            is ApiResult.Failure -> flowOf(tokenResult)    // 上游失败，直接透传，不再发第二个请求
        }
    }

// ---- 7.4 把“区块数据流”固化成 StateFlow（UI 端最佳实践，对应第六章的 Flow 化）----
// 用 map 把 ApiResult 转成 UI 状态密封类，再 stateIn 成可观察、可重放初始值的 StateFlow。
/*
val bannerState: StateFlow<BlockUiState<List<String>>> =
    getFlow<List<String>>("home/banner")
        .map { r -> when (r) {
            is ApiResult.Success -> BlockUiState.Ok(r.data)
            is ApiResult.Failure -> BlockUiState.Err(r.error.message ?: "失败")
        } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),  // 无订阅 5s 后停发，省流量/防泄漏
            initialValue = BlockUiState.Loading               // 立刻给 UI 一个 Loading，避免空值
        )
// 三个区块各来一个这样的 StateFlow，UI 各自 collect —— 即第六章“先到先渲染”的响应式表达。
*/

// ---- 7.5 杀手锏：搜索框防抖（输入 → 300ms 后发请求 → 新词自动取消旧请求）----
// EditText 连续输入会刷爆接口；Flow 的 debounce + flatMapLatest 一行搞定“防抖 + 取消旧请求”。
/*
private val queryState = MutableStateFlow("")

val searchResult: StateFlow<BlockUiState<List<User>>> =
    queryState
        .debounce(300)                          // 停止输入 300ms 才继续（防抖）
        .filter { it.length >= 2 }              // 至少 2 字才搜
        .distinctUntilChanged()                 // 相同词不重复发
        .flatMapLatest { kw ->                  // 新词到来：自动取消上一次还没完成的请求
            requestFlow { NetworkClient.http.get<List<User>>("search?q=$kw") }
                .map { r -> when (r) {
                    is ApiResult.Success -> BlockUiState.Ok(r.data)
                    is ApiResult.Failure -> BlockUiState.Err(r.error.message ?: "搜索失败")
                } }
                .onStart { emit(BlockUiState.Loading) }      // 每次新搜索先发 Loading
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BlockUiState.Loading)

// EditText 端（View 体系）：
//     editText.doOnTextChanged { text, _, _, _ -> queryState.value = text?.toString().orEmpty() }
// Compose 端：
//     OutlinedTextField(value = query, onValueChange = { queryState.value = it })
*/

// ============================================================
// 八、带参数的 Flow 请求封装（参数可多可少，调用便捷）
// =============================================================================================
// 第七章的 getFlow(path) 只收 path，太单薄。真实接口往往要带 query / headers / body / form，
// 且不同接口参数数量悬殊（有的 1 个，有的十几个）。本章给出一套“底层万能 + 上层便捷”的封装，
// 让“一两个参数”和“很多参数”都好写。
//
// 设计原则：
//   1) requestFlow { } 是万能底层 —— lambda 里能写任意参数组合，参数无论多少都一行搞定。
//   2) 便捷函数用“默认参数 + 命名实参”覆盖常见形态 —— 简单场景少打字，复杂场景也清晰。
//   3) 超多参数用 data class 承载 + toQuery() 转 Map —— 类型安全、可复用、可组合。
//   4) 任何便捷函数没覆盖的组合，直接回退底层 lambda，不必为每种组合都写函数。
// =============================================================================================

// ---- 8.1 便捷函数：用默认参数兼顾“一两个参数”和“很多参数” ----
// GET（query / headers 默认空，可只传 path，也可全填）
private inline fun <reified T> getFlow(
    path: String,
    query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
): Flow<ApiResult<T>> = requestFlow { NetworkClient.http.get<T>(path, query = query, headers = headers) }

// POST-JSON（body 必填，headers 默认空）
private inline fun <reified T> postJsonFlow(
    path: String,
    body: Any,
    headers: Map<String, String> = emptyMap(),
): Flow<ApiResult<T>> = requestFlow { NetworkClient.http.postJson<T>(path, body = body, headers = headers) }

// POST-form（fields 必填，headers 默认空）
private inline fun <reified T> postFormFlow(
    path: String,
    fields: Map<String, String>,
    headers: Map<String, String> = emptyMap(),
): Flow<ApiResult<T>> = requestFlow { NetworkClient.http.postForm<T>(path, fields = fields, headers = headers) }

// PUT-JSON
private inline fun <reified T> putJsonFlow(
    path: String,
    body: Any,
    headers: Map<String, String> = emptyMap(),
): Flow<ApiResult<T>> = requestFlow { NetworkClient.http.putJson<T>(path, body = body, headers = headers) }

// DELETE（headers 默认空）
private inline fun <reified T> deleteFlow(
    path: String,
    headers: Map<String, String> = emptyMap(),
): Flow<ApiResult<T>> = requestFlow { NetworkClient.http.delete<T>(path, headers = headers) }

// ---- 8.2 调用演示：参数少 vs 参数多 ----
// ① 一两个参数：直接用命名实参，清爽
private fun demoFewParams() {
    getFlow<User>("users/1")                                      // 无参
    getFlow<List<User>>("users", query = mapOf("page" to "1"))     // 1 个 query
    postJsonFlow<Unit>("users", body = CreateUserReq("Rocky", 30)) // body + 无 header
}

// ② 很多参数：先把参数提出来命名，避免一行超长、难读
private fun demoManyParams() {
    val query = mapOf(
        "keyword" to "kotlin",
        "category" to "book",
        "page" to "2",
        "sort" to "desc",
        "lang" to "zh",            // ... 还可以更多
    )
    val headers = mapOf("X-Trace" to "abc123", "X-Channel" to "app")
    getFlow<List<Item>>("search", query = query, headers = headers)
}

// ---- 8.3 超多 / 高频复用的参数：用 data class 承载 + toQuery() 转 Map（推荐）----
// 把“请求参数”做成类型安全的对象；toQuery() 集中负责映射，调用方只管构造对象。
private data class SearchParams(
    val keyword: String,
    val category: String = "",
    val page: Int = 1,
    val sort: String = "desc",
    val lang: String = "zh",
) {
    fun toQuery(): Map<String, String> = mapOf(
        "keyword" to keyword,
        "category" to category,
        "page" to "$page",         // 注意：query 的值都是 String，数字要转成字符串
        "sort" to sort,
        "lang" to lang,
    )
}

private fun demoWithDataClass() {
    val p = SearchParams(keyword = "kotlin", category = "book", page = 2)
    getFlow<List<Item>>("search", query = p.toQuery())   // 调用方极简，参数含义一目了然
}

// ---- 8.4 兜底：任何便捷函数没覆盖的组合，直接回退底层 lambda ----
// 例如“带 query 的 POST”“带 body 的 DELETE”等，不必为每种组合都造函数。
private fun demoFallback() {
    requestFlow {
        NetworkClient.http.postJson<Item>(
            "orders",
            body = mapOf("sku" to "x"),
            headers = mapOf("X-Sign" to "yyy"),
        )
    }
}

// ---- 8.5 参数化 Flow 与组合操作符一起用（承接第七章）----
// 带参并发（对应 7.1 zip）：
private fun demoParamZip() =
    getFlow<List<Item>>("search", query = mapOf("kw" to "a"))
        .zip(getFlow<User>("users/1", headers = mapOf("X" to "1"))) { a, b -> a to b }

// 带参依赖（对应 7.3 flatMapConcat，下一个请求用上一个的 token）：
private fun demoParamDependent() =
    postFormFlow<Token>("oauth/token", fields = mapOf("code" to "abc")).flatMapConcat { tr ->
        when (tr) {
            is ApiResult.Success ->
                getFlow<User>("users/me", headers = mapOf("Authorization" to "Bearer ${tr.data.accessToken}"))
            is ApiResult.Failure -> flowOf(tr)
        }
    }

// ============================================================
// 九、HTTP 错误（400 / 404 / 500 / 502 …）的统一处理
// ============================================================
// 1) 统一映射：只要响应码非 2xx（OkHttp 的 isSuccessful = 200..299），三条路径都收敛成同一个结构：
//      ApiResult.Failure(NetworkError(kind = Kind.HTTP, message = "HTTP 404",
//                                     httpCode = 404, responseBody = 错误体前 8192 字符))
//    - SimpleHttp（OkHttp）         -> SimpleHttp.failure()
//    - Retrofit 统一风格 ApiResult  -> ApiResultCall.toApiResult(response)
//    - Retrofit 裸风格 + safeApi    -> 捕获 HttpException（已补齐 errorBody）
//    注意：超时 / 连不上 / 读写失败分别是 TIMEOUT / CONNECTION / IO，与 HTTP 分开，别混在一起判断。
//
// 2) 全局统一副作用（推荐）：在 NetworkConfig 配一次，全站生效，不用每个接口都写 401 判断。
/*
NetworkClient.initialize(
    NetworkConfig(
        baseUrl = "https://api.example.com/",
        onHttpError = { code, body ->
            when {
                code == 401 -> SessionStore.logout()                 // 全局踢登录
                code in 500..599 -> reportServerError(code, body)    // 埋点 / 告警
            }
        },
    ),
)
*/
//    说明：这个钩子只做“副作用”，不会替换每个请求自己返回的 ApiResult.Failure，
//          调用方依然能拿到完整的 NetworkError（含 httpCode / responseBody）。
//
// 3) 语义扩展（避免魔法数字）：NetworkError 已提供下列属性与方法
//      isUnauthorized / isForbidden / isNotFound / isClientError / isServerError
//      userMessage()   // 面向用户的统一文案
/*
when (val r = NetworkClient.http.get<User>("users/1")) {
    is ApiResult.Success -> render(r.data)
    is ApiResult.Failure -> {
        if (r.error.isUnauthorized) goLogin()          // 不用再写 httpCode == 401
        else showError(r.error.userMessage())          // 统一文案
    }
}
*/

// ============================================================
// 附：在 ViewModel / Fragment 中“启动”协程（示例文件里不依赖 lifecycle，仅作注释参考）
// ============================================================
/*
// 在 ViewModel 中：
class DemoViewModel : androidx.lifecycle.ViewModel() {
    fun load() {
        viewModelScope.launch {                 // 主线程启动，网络在 IO 内部执行
            okhttpGet()                          // 调用上面的示例
        }
    }
}

// 在 Fragment / Activity 中：
lifecycleScope.launch { ... }                    // 跟随生命周期自动取消
viewLifecycleOwner.lifecycleScope.launch { ... } // Fragment 推荐用这个，避免视图销毁后更新 UI
*/

// ============================================================
// 内部辅助（仅为示例可编译；实际项目请删掉下列占位）
// ============================================================
private fun <T> handle(data: T, code: Int) = println("ok code=$code data=$data")
private fun handleError(error: NetworkError) = println("err kind=${error.kind} msg=${error.message}")
