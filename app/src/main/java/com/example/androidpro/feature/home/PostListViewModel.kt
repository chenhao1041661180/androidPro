package com.example.androidpro.feature.home

import com.example.androidpro.core.list.BaseListViewModel

class PostListViewModel(repository: PostListRepository) :
    BaseListViewModel<PostEntity, Post, PostRemoteKey>(repository)
