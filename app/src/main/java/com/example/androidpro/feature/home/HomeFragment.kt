package com.example.androidpro.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.example.androidpro.core.list.BaseListFragment
import com.example.androidpro.core.ui.viewModelFactory
import com.example.androidpro.data.DatabaseProvider
import com.example.androidpro.databinding.FragmentHomeBinding

/**
 * 首页列表示例：继承 BaseListFragment 后只需三样东西
 *  1) viewModel   2) bindingInflater   3) listAdapter
 *
 * 视图（RecyclerView / SwipeRefreshLayout / StateLayout）由基类按约定 id 自动查找，
 * 空 / 错 / 加载中页面由 StateLayout 全局统一提供，本页不再写任何 emptyView / errorView。
 */
class HomeFragment :
    BaseListFragment<FragmentHomeBinding, PostListViewModel, Post>() {

    override val viewModel: PostListViewModel by viewModels {
        viewModelFactory {
            val db = DatabaseProvider.database
            val cache = PostCache(db, db.postDao(), db.postRemoteKeyDao())
            PostListViewModel(PostListRepository(cache, PostApi()))
        }
    }

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentHomeBinding =
        { inflater, parent, attach -> FragmentHomeBinding.inflate(inflater, parent, attach) }

    override val listAdapter = PostPagingAdapter()

    override fun onSetupListView(savedInstanceState: Bundle?) {
        setTitleBar("首页")
        titleBar.setOnBackClickListener {
            activity?.finish()
        }
    }
}
