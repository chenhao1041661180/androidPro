package com.example.androidpro.core.list

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * 通用 DAO 基类（不标注 @Dao，由具体 @Dao 接口继承）。
 * 子类只需补充带 @Query 的方法，例如 Room 的 pagingSource()。
 */
interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<T>)

    @Update
    suspend fun update(entity: T)

    @Delete
    suspend fun delete(entity: T)

    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<T>

    @RawQuery
    suspend fun clear(query: SupportSQLiteQuery): Int
}

/** 按表名清空整张表。 */
suspend fun BaseDao<*>.clearTable(tableName: String) =
    clear(SimpleSQLiteQuery("DELETE FROM `$tableName`"))
