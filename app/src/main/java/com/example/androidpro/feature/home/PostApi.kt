package com.example.androidpro.feature.home

import com.example.androidpro.core.list.ListApi
import com.example.androidpro.core.network.ApiResult
import com.example.androidpro.core.network.NetworkClient
import com.example.androidpro.core.network.map

class PostApi : ListApi<PostEntity> {
    override suspend fun loadPage(page: Int, pageSize: Int): ApiResult<List<PostEntity>> =
        NetworkClient.http.get<List<Post>>(
            path = "posts",
            query = mapOf("_page" to page, "_limit" to pageSize),
        ).map { posts -> posts.map { PostEntity(it.id, it.userId, it.title, it.body) } }
}
