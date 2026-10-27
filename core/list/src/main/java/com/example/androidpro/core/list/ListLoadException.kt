package com.example.androidpro.core.list

import com.example.androidpro.core.network.NetworkError

/**
 * RemoteMediator 把 [com.example.androidpro.core.network.ApiResult.Failure]
 * 转换为 Paging 可识别的异常，并保留结构化错误（用于 UI 展示文案）。
 */
class ListLoadException(val error: NetworkError) : Exception(error.message)
