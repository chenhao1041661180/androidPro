package com.example.androidpro.core.list

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 组装 RemoteMediator + Pager，输出映射后的 [PagingData] 流。
 * 任意列表页只需提供 api / cache / Entity->Item 映射即可复用。
 *
 * 注意：Pager 的 remoteMediator 重载属于 [ExperimentalPagingApi]，故在类级别显式 opt-in。
 */
@OptIn(ExperimentalPagingApi::class)
open class ListRepository<Entity : Any, Item : Any, Key : RemoteKey>(
    private val api: ListApi<Entity>,
    private val cache: ListCache<Entity, Key>,
    private val mapper: (Entity) -> Item,
    pageSize: Int = 20,
) {
    private val mediator = ListRemoteMediator(api, cache)
    private val pager = Pager(
        config = PagingConfig(
            pageSize = pageSize,
            // 关键：RemoteMediator 场景下 initialLoadSize 必须与 pageSize 一致。
            // PagingConfig 默认 initialLoadSize = pageSize * 3，而 RemoteMediator 的 REFRESH
            // 只会拉 1 页；Paging 为了凑够 initialLoadSize，会在启动时连续触发 APPEND
            // （pageSize=20 时会一次拉 1/2/3 页，再叠加 prefetchDistance 拉第 4 页）。
            initialLoadSize = pageSize,
            prefetchDistance = 1,
            enablePlaceholders = false,
        ),
        remoteMediator = mediator,
    ) { cache.pagingSource() }

    val pagingData: Flow<PagingData<Item>> = pager.flow.map { it.map(mapper) }
}
