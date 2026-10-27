package com.example.androidpro.core.list

import androidx.paging.PagingSource
import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * [ListCache] 的通用实现。子类只需补充 [pagingSource]（调用具体 Dao 的 @Query）。
 *
 * @param database      Room 实例，用于把多次写操作合并成**一个**事务（见 [transaction]）
 * @param entityDao     实体 DAO（继承 [BaseDao]）
 * @param remoteKeyDao 游标 DAO（继承 [BaseRemoteKeyDao]）
 * @param tableName    实体表名（用于整表清空）
 * @param listId       该列表在 remote_keys 中的唯一 id
 * @param keyFactory   由 nextPage 构造游标实体
 */
abstract class BaseListCache<Entity : Any, Key : RemoteKey>(
    private val database: RoomDatabase,
    private val entityDao: BaseDao<Entity>,
    private val remoteKeyDao: BaseRemoteKeyDao<Key>,
    private val tableName: String,
    private val listId: String,
    private val keyFactory: (Int?) -> Key,
) : ListCache<Entity, Key> {

    abstract override fun pagingSource(): PagingSource<Int, Entity>

    override suspend fun insertPage(page: Int, items: List<Entity>, endReached: Boolean) {
        entityDao.insertAll(items)
        remoteKeyDao.insert(keyFactory(if (endReached) null else page + 1))
    }

    override suspend fun clearAll() {
        entityDao.clearTable(tableName)
        remoteKeyDao.clear(listId)
    }

    override suspend fun nextPage(): Int? = remoteKeyDao.get(listId)?.nextPage

    override suspend fun <R> transaction(block: suspend () -> R): R = database.withTransaction(block)
}
