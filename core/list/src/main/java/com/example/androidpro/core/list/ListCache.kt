package com.example.androidpro.core.list

import androidx.paging.PagingSource

/**
 * 本地缓存契约：Room 作为 Paging 的数据源，并提供写入 / 清空 / 游标读取。
 */
interface ListCache<Entity : Any, Key : RemoteKey> {
    /** Paging3 的 PagingSource（通常来自 Room Dao.pagingSource()）。 */
    fun pagingSource(): PagingSource<Int, Entity>

    /** 写入某一页数据（同时更新分页游标）。endReached 表示已到末尾。 */
    suspend fun insertPage(page: Int, items: List<Entity>, endReached: Boolean)

    /** 清空该列表的缓存与游标（下拉刷新前调用）。 */
    suspend fun clearAll()

    /** 下一页页码；null 表示已到末尾或尚未加载。 */
    suspend fun nextPage(): Int?

    /**
     * 在同一个数据库事务内执行 [block]（务必保证：网络请求在事务**之外**完成，
     * 只有「清表 + 写数据 + 更新游标」这三步进事务）。
     *
     * RemoteMediator 必须这么写的原因有三条：
     *
     * 1. **原子性**：不清表就插入会留下脏旧数据；清了表却在插入前失败，列表会直接空屏。
     *    事务保证"要么都在，要么都不在"。
     * 2. **游标与数据一致**：数据行与 `remote_keys.nextPage` 必须同时提交，
     *    否则中途被别的加载读到，会拿到"有数据但无游标 / 游标已推进但无数据"的半截状态。
     * 3. **减少 PagingSource 抖动**：Room 每提交一次写事务就会 notify 一次 InvalidationTracker，
     *    PagingSource 随之失效、随即重建出一个新的 generation。
     *    3 次独立写 = 3 次失效 = 列表重建 3 次（UI 重排 + 额外的边界加载）。
     *    合并成一个事务后只 notify 一次，只有 1 个额外 generation。
     *    注意：由失效产生的新 generation 其 `triggerRemoteRefresh = false`，
     *    不会再触发一次 RemoteMediator 的远程 REFRESH（真正的上游请求仍然只有一次）。
     */
    suspend fun <R> transaction(block: suspend () -> R): R
}
