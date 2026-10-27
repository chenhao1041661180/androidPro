package com.example.androidpro.core.list

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/**
 * 列表界面状态（由 [CombinedLoadStates] + itemCount 派生），供 UI 切换 加载/空/错/刷新 视图。
 */
sealed interface ListUiState {
    data object Idle : ListUiState
    data object LoadingInitial : ListUiState // 首次加载（尚无数据）
    data object Refreshing : ListUiState      // 下拉刷新（已有数据）
    data object LoadingMore : ListUiState     // 上拉加载更多
    data class Error(val message: String, val retryable: Boolean = true) : ListUiState
    data object Empty : ListUiState           // 加载成功但无数据
    data object Success : ListUiState
}

fun CombinedLoadStates.toListUiState(itemCount: Int): ListUiState {
    val refresh = refresh
    val append = append
    return when {
        refresh is LoadState.Loading && itemCount == 0 -> ListUiState.LoadingInitial
        refresh is LoadState.Loading -> ListUiState.Refreshing
        refresh is LoadState.Error -> ListUiState.Error(
            message = (refresh.error as? ListLoadException)?.error?.message
                ?: refresh.error.message ?: "加载失败",
            retryable = true,
        )
        append is LoadState.Loading -> ListUiState.LoadingMore
        refresh is LoadState.NotLoading && itemCount == 0 -> ListUiState.Empty
        else -> ListUiState.Success
    }
}
