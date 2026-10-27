package com.example.androidpro.feature.home

import com.example.androidpro.core.list.ListRepository

class PostListRepository(
    cache: PostCache,
    api: PostApi,
) : ListRepository<PostEntity, Post, PostRemoteKey>(api, cache, { it.toPost() })
