# AndroidPro

面向 XML/ViewBinding 项目的 Kotlin Android 基础框架。包含 Activity/Fragment/ViewModel 基类、生命周期安全的 Flow 收集、统一网络入口、Paging 3 长列表示例，以及 BRVAH、ShapeView、Material Components 依赖。

## 环境

- Android Studio Narwhal 4 / Otter 或其他支持 AGP 8.13 的版本
- JDK 17
- Android SDK 36
- Gradle 8.13（使用项目 wrapper）

首次打开后把 `app/build.gradle.kts` 的 release `BASE_URL` 换成真实 HTTPS 地址。Debug 使用 JSONPlaceholder 展示分页请求。

```bash
./gradlew :app:assembleDebug
./gradlew test
```

## 目录

```text
app/           示例应用和业务 feature
core/common/   UiState、BaseViewModel 等公共契约
core/network/  OkHttp/Retrofit、统一请求与错误模型
core/ui/       Activity、Fragment、Paging Adapter 基类及 UI 依赖
core/list/     通用列表框架：Paging3 + Room + RemoteMediator + 下拉刷新/上拉加载/网络异常
```

## 网络调用

应用只需在 `Application` 初始化一次。业务 Repository 可直接使用简化 API：

```kotlin
val http = NetworkClient.http

http.get<User>("users/1")
http.get<List<User>>("users", mapOf("page" to 1))
http.postJson<User>("users", CreateUser(name = "Ada"))
http.putJson<User>("users/1", UpdateUser(name = "Grace"))
http.delete<Unit>("users/1")
http.deleteJson<Unit>("users/1", mapOf("reason" to "duplicate"))
http.postForm<Token>("oauth/token", mapOf("code" to code))
http.upload<UploadResult>("files", file, fields = mapOf("type" to "avatar"))
http.download("files/report", File(requireContext().cacheDir, "report.pdf"))
```

每个调用返回 `ApiResult<T>`，统一区分 HTTP、超时、连接、序列化和 I/O 错误。协程被取消时会同步取消 OkHttp Call。若项目更适合声明式接口，使用 `NetworkClient.service<MyApi>()` 获取共享同一客户端的 Retrofit Service。

上传、下载提供 `(currentBytes, totalBytes)` 进度回调。进度回调发生在 I/O 线程，如需直接更新 View，切回主线程或转成 Flow/StateFlow。下载目标建议使用应用私有目录；写公共目录时接入系统 Storage Access Framework。

## 列表框架（core/list）

通用「网络 + Room 本地缓存 + Paging3」列表方案，任意列表页都能复用：

- `BaseListViewModel<Entity, Item, Key>`：暴露 `pagingData` 流（已 `cachedIn`）。
- `BaseListFragment<VB, VM, Item>`：自动装配下拉刷新、上拉加载脚、缺省页（StateLayout）。`Entity`/`Key` 由 `VM` 自身携带、用星投影自动推断。
  - **子类只需提供三项**：`viewModel`、`bindingInflater`、`listAdapter`。
  - **视图按约定 id 自动查找**（id 定义在 `core/ui` 的 `ids.xml`）：`state`（StateLayout）、`list`（RecyclerView）、`swipeRefresh`（可选）。用了别的 id 才需要单独 override 对应属性。
  - **缺省页全局统一**：loading / empty / error 三套布局由 `StateLayoutDefaults` 在 Application 里配置一次，全站复用，各页面不再写 emptyView / errorView / retryView。
- `ListApi` / `ListCache` / `BaseListCache`：网络分页与 Room 缓存契约。`BaseListCache` 第一个参数是 `RoomDatabase`（透传进来即可），用于把写入放进一个事务。
- `ListRemoteMediator`：REFRESH 清表拉首页、APPEND 拉下一页并写入 Room；断网时直接读缓存秒出。
  - **写库必须走 `cache.transaction{}`**：「清表 + 写数据 + 更新游标」一次性提交，保证游标与数据一致，并把 PagingSource 失效次数从 3 次降到 1 次。
  - **网络请求留在事务外**：先把结果取回来，再进事务落库，避免长时间占用数据库连接。
  - **APPEND 走游标 CAS**：网络往返期间 Room 失效会产生新 generation，它们会读到同一个 `nextPage`。提交前二次比对游标，抢不到的一边丢弃结果，避免同一页被拉两次。
- 网络异常复用现有的 `ApiResult` / `NetworkError` 分类。

新增一个列表页只需：定义 Entity/Dao、实现 `ListApi` 与 `ListCache`、组装 `ListRepository`、写一个继承 `BaseListViewModel` 的 VM 和一个继承 `BasePagingAdapter` 的 Adapter，Fragment 继承 `BaseListFragment` 并给出 `viewModel` / `bindingInflater` / `listAdapter` 即可；布局用 `StateLayout` 包裹 `SwipeRefreshLayout`+`RecyclerView`，并按约定命名 id。`feature/home` 即为可运行示例。

## 依赖取舍

- Paging 3 + `BasePagingAdapter` 是大数据分页默认路径。
- BRVAH 适合普通列表、多类型和组合 Adapter；无需分页时可减少 Adapter 样板代码。
- ShapeView 用 XML 属性完成圆角、描边和状态背景；示例页已接入。
- StateLayout（`com.github.liangjingkanji:StateLayout`）统一承载页面缺省页（loading / empty / error），避免每个页面各写一套空/错视图。
- Material Components 提供主题与标准控件。

依赖版本集中在 `gradle/libs.versions.toml`，不要在模块内散落版本号或使用动态 `+` 版本。
