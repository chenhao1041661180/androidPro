package com.example.androidpro.feature.home

import androidx.paging.PagingSource
import androidx.room.RoomDatabase
import com.example.androidpro.core.list.BaseListCache

const val POSTS_LIST_ID = "posts"

/**
 * @param database Room 实例：[com.example.androidpro.core.list.ListRemoteMediator]
 *   需要把"清表 + 写数据 + 更新游标"放进同一个事务，这里透传给它。
 */
class PostCache(
    database: RoomDatabase,
    private val dao: PostDao,
    private val keyDao: PostRemoteKeyDao,
) : BaseListCache<PostEntity, PostRemoteKey>(
    database,
    dao,
    keyDao,
    tableName = "posts",
    listId = POSTS_LIST_ID,
    keyFactory = { PostRemoteKey(POSTS_LIST_ID, it) },
) {
    override fun pagingSource(): PagingSource<Int, PostEntity> = dao.pagingSource()
}
