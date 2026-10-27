# 架构与 UI

依赖方向是 `app → core/ui → core/common`、`app → core/network → core/common`。core 不得反向引用 app。

业务 feature 内按规模放置 Fragment/Activity、ViewModel、Repository、DTO/模型和列表组件。简单功能无需机械增加 use-case 层；业务被多个 ViewModel 复用或包含复杂组合规则时再抽取。

ViewBinding 的生命周期由基类管理。Fragment 的 binding 只在 `onCreateView` 到 `onDestroyView` 之间使用；异步状态收集绑定 `viewLifecycleOwner`。ViewModel 的任务使用 `viewModelScope`，页面退出后无订阅者的 UI 状态不应继续驱动 View。

PagingSource 将 `ApiResult.Failure` 映射为 `LoadResult.Error`，页面依据 `loadStateFlow` 展示首次加载和追加状态。稳定 id 用于 `DiffUtil` 的 identity，内容比较覆盖影响展示的字段。

ShapeView 当前坐标为 `com.github.getActivity:ShapeView:11.0`，仓库因此配置 JitPack。布局使用 `com.hjq.shape.*` 组件；只在 ShapeView 无法表达时新增 drawable。
