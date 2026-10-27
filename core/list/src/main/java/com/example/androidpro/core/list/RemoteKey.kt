package com.example.androidpro.core.list

/**
 * 分页游标（remote key）实体的统一契约。
 * 所有列表的游标实体都必须实现它，从而让框架在不知道具体类型的情况下
 * 也能安全读取 [nextPage]。
 *
 * nextPage 为 null 表示已到末尾或尚未加载。
 */
interface RemoteKey {
    val nextPage: Int?
}
