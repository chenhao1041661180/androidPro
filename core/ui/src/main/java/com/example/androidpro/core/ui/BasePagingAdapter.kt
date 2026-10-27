package com.example.androidpro.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

abstract class BasePagingAdapter<T : Any, VB : ViewBinding>(diff: DiffUtil.ItemCallback<T>) :
    PagingDataAdapter<T, BasePagingAdapter.BindingHolder<VB>>(diff) {

    protected abstract fun inflate(inflater: LayoutInflater, parent: ViewGroup): VB
    protected abstract fun bind(binding: VB, item: T, position: Int)

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<VB> =
        BindingHolder(inflate(LayoutInflater.from(parent.context), parent))

    final override fun onBindViewHolder(holder: BindingHolder<VB>, position: Int) {
        getItem(position)?.let { bind(holder.binding, it, position) }
    }

    class BindingHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root)
}
