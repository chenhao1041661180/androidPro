package com.example.androidpro.feature.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.example.androidpro.core.ui.BasePagingAdapter
import com.example.androidpro.databinding.ItemPostBinding

class PostPagingAdapter : BasePagingAdapter<Post, ItemPostBinding>(Diff) {
    override fun inflate(inflater: LayoutInflater, parent: ViewGroup): ItemPostBinding =
        ItemPostBinding.inflate(inflater, parent, false)

    override fun bind(binding: ItemPostBinding, item: Post, position: Int) = with(binding) {
        title.text = item.title
        body.text = item.body
    }

    private object Diff : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Post, newItem: Post) = oldItem == newItem
    }
}
