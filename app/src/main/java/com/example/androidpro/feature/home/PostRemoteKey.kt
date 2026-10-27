package com.example.androidpro.feature.home

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.androidpro.core.list.RemoteKey

@Entity(tableName = "remote_keys")
data class PostRemoteKey(
    @PrimaryKey @ColumnInfo(name = "key_id") val id: String,
    @ColumnInfo(name = "next_page") override val nextPage: Int?,
) : RemoteKey
