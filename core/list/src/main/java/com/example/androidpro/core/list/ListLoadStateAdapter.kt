package com.example.androidpro.core.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidpro.core.list.databinding.ListLoadStateFooterBinding

/**
 * 通用上拉加载脚：加载中 / 加载失败（点击重试）/ 没有更多了。
 */
class ListLoadStateAdapter(
    private val retry: () -> Unit,
) : LoadStateAdapter<ListLoadStateAdapter.ViewHolder>() {

    class ViewHolder(val binding: ListLoadStateFooterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun displayLoadStateAsItem(loadState: LoadState): Boolean =
        loadState is LoadState.Loading ||
            loadState is LoadState.Error ||
            (loadState is LoadState.NotLoading && loadState.endOfPaginationReached)

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): ViewHolder =
        ViewHolder(ListLoadStateFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, loadState: LoadState) = with(holder.binding) {
        when (loadState) {
            is LoadState.Loading -> {
                progress.isVisible = true
                btnRetry.isVisible = false
                text.isVisible = false
            }
            is LoadState.Error -> {
                progress.isVisible = false
                btnRetry.isVisible = true
                text.isVisible = true
                text.text = (loadState.error as? ListLoadException)?.error?.message
                    ?: loadState.error.message ?: "加载失败"
            }
            is LoadState.NotLoading -> {
                progress.isVisible = false
                btnRetry.isVisible = false
                text.isVisible = loadState.endOfPaginationReached
                text.text = "没有更多了"
            }
        }
        btnRetry.setOnClickListener { retry() }
    }
}
