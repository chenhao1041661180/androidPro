---
name: androidpro-android-dev
description: AndroidPro 原生 Android 项目的开发规范。用于新增或修改 Activity、Fragment、ViewModel、XML/ViewBinding 页面、Paging/RecyclerView 列表、ShapeView UI，以及 GET、POST、DELETE、PUT、表单、JSON、上传或下载请求。
---

# AndroidPro 开发规范

保持 Kotlin + XML/ViewBinding + MVVM 分层，复用项目基础类和统一网络入口。不要在普通业务需求中引入第二套网络、依赖注入或状态管理框架。

## 开始前

读取根目录 `AGENTS.md`、`gradle/libs.versions.toml` 以及与任务最近的现有 feature。新增页面前阅读 [架构与 UI](references/architecture.md)；新增接口、上传或下载前阅读 [网络规范](references/networking.md)。调整依赖时阅读 [依赖与版本](references/dependencies.md)。

## 实现约定

- Activity 继承 `BaseActivity<VB, VM>`，Fragment 继承 `BaseFragment<VB, VM>`；分别在 `setupView`、`observeState`、`loadData` 完成职责。
- ViewModel 只持有 Repository/业务状态，不持有 View、Activity、Fragment 或长期 BuildContext 等 UI 对象。页面 Flow 使用 `repeatOnLifecycle`；Fragment 始终用 `viewLifecycleOwner`。
- Repository 是业务层唯一的网络入口。优先用 `NetworkProvider.http` 的简化请求；需要 Retrofit 注解或复杂接口时使用 `NetworkProvider.service<T>()`，仍复用共享客户端。
- 页面状态明确表达 loading、empty、error、content。一次性提示使用 SharedFlow；不要用布尔字段模拟所有状态，也不要吞掉协程取消。
- 长列表优先 Paging 3 和 `BasePagingAdapter`。普通、多类型或组合列表可以使用 BRVAH；不要同时用两套 Adapter 管理同一个 RecyclerView。
- 圆角、描边等常规 XML 背景优先 ShapeView，标准交互控件优先 Material Components。字符串写入资源文件，并保留无障碍描述。

## 验证

按改动范围执行：

```bash
./gradlew :app:assembleDebug
./gradlew test
```

新增网络流程至少走查成功、HTTP 失败、超时/离线、取消；分页页面还要走查首屏失败、刷新、追加失败和列表恢复。
