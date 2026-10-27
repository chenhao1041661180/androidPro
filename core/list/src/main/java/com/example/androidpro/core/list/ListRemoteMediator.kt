package com.example.androidpro.core.list

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.androidpro.core.network.ApiResult
import kotlinx.coroutines.CancellationException

/** RemoteMediator 的首页页码。 */
private const val FIRST_PAGE = 1

private const val REMOTE_MEDIATOR_TAG = "RemoteMediator"

/**
 * 通用 RemoteMediator：网络 -> Room -> Paging。
 *
 * - REFRESH：清空缓存后拉首页并写入 Room（断网时保留旧缓存，展示历史数据）。
 * - APPEND：依据游标拉下一页并写入 Room。
 * - 网络失败：转 [androidx.paging.RemoteMediator.MediatorResult.Error]，
 *   由 LoadState.Error 在 UI 呈现并可重试。
 *
 * ## 两个必须遵守的约定
 *
 * ### 1. 写库放在一个事务里
 * 「清表 + 写数据 + 更新游标」通过 [ListCache.transaction] 一次性提交：
 * 既保证游标与数据一致，也把多次写引发的 PagingSource 失效从 3 次降到 1 次，
 * 避免列表反复重建 generation（UI 重排 + 多余的边界加载）。
 * 网络请求留在事务之外，避免长时间占用数据库连接。
 *
 * ### 2. APPEND 的游标必须 CAS（比较再写）
 * `远端请求 -> 写库` 之间隔着一次网络往返。这期间 Room 的写入会失效重建出一个新的
 * generation，它同样会走到 APPEND 并读到**同一个** `nextPage`。
 * 若不校验，两（多）个并发加载会拉同一页、写两份数据 —— 表现就是"同一页被请求两次"。
 * 因此提交前要再比一次游标：只有游标没变才写入，变了说明别人已经写过了，本地数据已就绪，
 * 本次结果直接丢弃。
 */
@OptIn(ExperimentalPagingApi::class)
class ListRemoteMediator<Entity : Any, Key : RemoteKey>(
    private val api: ListApi<Entity>,
    private val cache: ListCache<Entity, Key>,
) : RemoteMediator<Int, Entity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Entity>,
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.REFRESH -> loadRefresh(state)
                LoadType.APPEND -> loadAppend(state)
                // 服务端只支持向下翻页，向上没有更早的数据
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            MediatorResult.Error(e)
        }
    }

    private suspend fun loadRefresh(state: PagingState<Int, Entity>): MediatorResult {
        Log.d(REMOTE_MEDIATOR_TAG, "load REFRESH page=$FIRST_PAGE size=${state.config.pageSize}")
        val result = api.loadPage(FIRST_PAGE, state.config.pageSize)
        return when (result) {
            is ApiResult.Failure -> MediatorResult.Error(ListLoadException(result.error))
            is ApiResult.Success -> {
                val endReached = result.data.size < state.config.pageSize
                cache.transaction {
                    cache.clearAll()
                    cache.insertPage(FIRST_PAGE, result.data, endReached)
                }
                MediatorResult.Success(endOfPaginationReached = endReached)
            }
        }
    }

    private suspend fun loadAppend(state: PagingState<Int, Entity>): MediatorResult {
        val expected = cache.nextPage()
            ?: return MediatorResult.Success(endOfPaginationReached = true)

        Log.d(REMOTE_MEDIATOR_TAG, "load APPEND page=$expected size=${state.config.pageSize}")
        val result = api.loadPage(expected, state.config.pageSize)
        val data = when (result) {
            is ApiResult.Failure -> return MediatorResult.Error(ListLoadException(result.error))
            is ApiResult.Success -> result.data
        }
        val endReached = data.size < state.config.pageSize

        val committed = cache.transaction {
            // 游标 CAS：只有游标仍是 expected 才提交，抢不到的一边直接丢弃自己的结果，
            // 让 Paging 从 Room 重新读取由赢家写入的数据。
            if (cache.nextPage() != expected) {
                Log.d(REMOTE_MEDIATOR_TAG, "丢弃重复页: page=$expected（已被并发加载写入）")
                false
            } else {
                cache.insertPage(expected, data, endReached)
                true
            }
        }
        // 没提交成功 => 数据其实已经在本地了，按"已加载完"汇报即可，不要再拉一遍
        if (!committed) return MediatorResult.Success(endOfPaginationReached = cache.nextPage() == null)

        return MediatorResult.Success(endOfPaginationReached = endReached)
    }
}
