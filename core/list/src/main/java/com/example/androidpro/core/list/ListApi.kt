package com.example.androidpro.core.list

import com.example.androidpro.core.network.ApiResult

/**
 * 网络分页契约。返回 ApiResult，直接复用项目已有的网络异常分类（HTTP/TIMEOUT/CONNECTION/...）。
 */
interface ListApi<Entity> {
    suspend fun loadPage(page: Int, pageSize: Int): ApiResult<List<Entity>>
}
