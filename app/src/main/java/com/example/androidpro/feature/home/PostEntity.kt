package com.example.androidpro.feature.home

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
) {
    fun toPost(): Post = Post(id, userId, title, body)
}
