package com.example.androidpro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.androidpro.feature.home.PostDao
import com.example.androidpro.feature.home.PostEntity
import com.example.androidpro.feature.home.PostRemoteKey
import com.example.androidpro.feature.home.PostRemoteKeyDao

@Database(
    entities = [PostEntity::class, PostRemoteKey::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun postRemoteKeyDao(): PostRemoteKeyDao
}

object DatabaseProvider {
    private lateinit var db: AppDatabase

    fun init(context: Context) {
        db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "androidpro.db",
        ).fallbackToDestructiveMigration().build()
    }

    val database: AppDatabase get() = db
}
