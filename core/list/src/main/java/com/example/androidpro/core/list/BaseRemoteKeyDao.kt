package com.example.androidpro.core.list

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 分页游标（remote key）通用 DAO：所有列表共用一张 `remote_keys` 表，用 id 区分不同列表。
 * 具体 @Dao 接口继承它即可，无需再写 SQL。
 *
 * nextPage 为 null 表示已到末尾或尚未加载。
 */
interface BaseRemoteKeyDao<Key : RemoteKey> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: Key)

    @Query("SELECT * FROM remote_keys WHERE key_id = :id")
    suspend fun get(id: String): Key?

    @Query("DELETE FROM remote_keys WHERE key_id = :id")
    suspend fun clear(id: String)
}
