package com.example.androidpro.feature.home

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.example.androidpro.core.list.BaseDao

@Dao
interface PostDao : BaseDao<PostEntity> {
    @Query("SELECT * FROM posts ORDER BY id ASC")
    fun pagingSource(): PagingSource<Int, PostEntity>
}
