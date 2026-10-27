package com.example.androidpro.core.list

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewbinding.ViewBinding
import com.drake.statelayout.StateLayout
import com.example.androidpro.core.ui.BaseFragment
import com.example.androidpro.core.ui.BasePagingAdapter
import com.example.androidpro.core.ui.R as UiR
import com.example.androidpro.core.ui.state.StateLayoutDefaults
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 通用列表 Fragment：下拉刷新 + RecyclerView + 上拉加载 + 缺省页（StateLayout）全自动装配。
 *
 * ## 子类只需要提供三样东西
 * - [viewModel]（继承 [BaseListViewModel]，由 [BaseFragment] 要求）
 * - [bindingInflater]（ViewBinding，由 [BaseFragment] 要求）
 * - [listAdapter]（本页的 PagingDataAdapter）
 *
 * ## 视图不再需要逐个 override
 * 以下视图按**约定 id** 自动查找（id 定义在 core:ui 的 ids.xml，资源合并后全项目通用）：
 * - `state`         -> [stateLayout]（StateLayout 缺省页容器）
 * - `list`          -> [recyclerView]
 * - `swipeRefresh`  -> [swipeRefreshLayout]（可缺省）
 * 若某个页面用了别的 id，单独 override 对应属性即可。
 *
 * ## 状态页
 * 由 StateLayout 统一接管（loading / empty / error / content），页面**不需要**
 * 再写 emptyView / errorView / errorTextView / retryView；三套缺省页布局由
 * [StateLayoutDefaults] 在 Application 里全局配置一次，全站复用。
 */
abstract class BaseListFragment<VB : ViewBinding, VM, Item : Any> : BaseFragment<VB, VM>()
        where VM : BaseListViewModel<*, Item, *> {

    // ==================== 子类必须提供 ====================
    protected abstract val listAdapter: BasePagingAdapter<Item, *>

    // ==================== 视图：按约定 id 自动查找 ====================
    /** 缺省页容器；找不到则不切换状态页（纯列表页可不管） */
    protected open val stateLayout: StateLayout?
        get() = binding.root.findViewById<StateLayout>(UiR.id.state)

    protected open val recyclerView: RecyclerView
        get() = checkNotNull(binding.root.findViewById<RecyclerView>(UiR.id.list)) {
            "列表页布局需包含 id 为 `list` 的 RecyclerView，或 override recyclerView"
        }

    protected open val swipeRefreshLayout: SwipeRefreshLayout?
        get() = binding.root.findViewById<SwipeRefreshLayout>(UiR.id.swipeRefresh)

    protected open val layoutManager: RecyclerView.LayoutManager
        get() = LinearLayoutManager(requireContext())

    /** 最近一次错误文案，展示在错误缺省页上 */
    private var lastErrorMessage: String? = null

    // ==================== 装配 ====================
    final override fun setupView(savedInstanceState: Bundle?) {
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = listAdapter.withLoadStateFooter(ListLoadStateAdapter { listAdapter.retry() })
        swipeRefreshLayout?.setOnRefreshListener { listAdapter.refresh() }
        bindStateRetry()
        onSetupListView(savedInstanceState)
    }

    /**
     * 把缺省页上的"重试"按钮接到 Paging 的重试 / 刷新上。
     * 这里用手动监听而不是 StateConfig.setRetryIds：后者只会切换 UI，
     * 不会真正重新发起请求；手动监听能保证"点击 -> 真的重新加载"。
     */
    private fun bindStateRetry() {
        val state = stateLayout ?: return
        val retryId = StateLayoutDefaults.retryId
        val messageId = StateLayoutDefaults.messageId
        state.onError {
            findViewById<TextView>(messageId)?.text = lastErrorMessage ?: "加载失败"
            findViewById<View>(retryId)?.setOnClickListener { listAdapter.retry() }
        }
        state.onEmpty {
            findViewById<View>(retryId)?.setOnClickListener { listAdapter.refresh() }
        }
    }

    final override fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.pagingData.collectLatest { listAdapter.submitData(it) } }
                launch {
                    listAdapter.loadStateFlow.collectLatest { states ->
                        applyListState(states.toListUiState(listAdapter.itemCount))
                    }
                }
            }
        }
        onObserveListState()
    }

    private fun applyListState(state: ListUiState) {
        swipeRefreshLayout?.isRefreshing = state is ListUiState.Refreshing
        stateLayout?.let { sl ->
            when (state) {
                ListUiState.Idle -> Unit
                ListUiState.LoadingInitial -> sl.showLoading()
                // 已有数据时下拉刷新只转圈，不遮内容
                ListUiState.Refreshing -> if (listAdapter.itemCount == 0) sl.showLoading() else sl.showContent()
                ListUiState.LoadingMore -> sl.showContent()
                ListUiState.Empty -> sl.showEmpty()
                ListUiState.Success -> sl.showContent()
                is ListUiState.Error -> {
                    lastErrorMessage = state.message
                    sl.showError(state.message)
                }
            }
        }
        onListStateChanged(state)
    }

    // ==================== 可选钩子 ====================
    protected open fun onSetupListView(savedInstanceState: Bundle?) = Unit
    protected open fun onObserveListState() = Unit
    protected open fun onListStateChanged(state: ListUiState) = Unit
}
