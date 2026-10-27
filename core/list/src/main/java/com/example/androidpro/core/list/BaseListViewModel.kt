package com.example.androidpro.core.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.androidpro.core.common.BaseViewModel
import kotlinx.coroutines.flow.Flow

/**
 * 列表页 ViewModel 基类。仅负责提供 PagingData 流（已 [cachedIn]），
 * 网络异常的消息总线继承自 [BaseViewModel]（messages）。
 *
 * 子类传入 [ListRepository]，UI 端收集 pagingData 提交给 Adapter 即可。
 */
abstract class BaseListViewModel<Entity : Any, Item : Any, Key : RemoteKey>(
    repository: ListRepository<Entity, Item, Key>,
) : BaseViewModel() {
    val pagingData: Flow<PagingData<Item>> = repository.pagingData.cachedIn(viewModelScope)
}
