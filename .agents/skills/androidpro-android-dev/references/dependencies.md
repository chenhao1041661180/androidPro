# 依赖与版本

版本统一在 `gradle/libs.versions.toml`。新增库前确认已有能力、维护状态、许可证、minSdk/compileSdk、Kotlin/AGP 与 R8 兼容性；不要使用动态版本。

当前兼容基线于 2026-09-12 核验：AGP 8.13.0 + Gradle 8.13 + Kotlin 2.2.21 + JDK 17；Lifecycle 2.11.0；Paging 3.5.1；RecyclerView 1.4.0；Retrofit 3.0.0；OkHttp BOM 5.3.0；BRVAH 4.1.4；ShapeView 11.0。项目因本机 SDK 和 Android Studio 支持范围选择 compileSdk/targetSdk 36。BRVAH 4.1.4 是为避免 4.4.1 的 Kotlin 元数据超出 AGP 8.13.0 内置 D8 能力而采用的兼容版本。

官方或维护者资料：

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://developer.android.com/jetpack/androidx/releases/paging
- https://github.com/square/retrofit
- https://github.com/square/okhttp
- https://github.com/CymChad/BaseRecyclerViewAdapterHelper
- https://github.com/getActivity/ShapeView

升级前重新读取发布说明并运行编译与测试；这里的核验日期不代表未来兼容性。
