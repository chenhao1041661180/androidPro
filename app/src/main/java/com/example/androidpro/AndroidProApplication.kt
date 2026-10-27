package com.example.androidpro

import android.app.Application
import com.example.androidpro.core.network.NetworkClient
import com.example.androidpro.core.network.NetworkConfig
import com.example.androidpro.core.ui.state.StateLayoutDefaults
import com.example.androidpro.data.DatabaseProvider

class AndroidProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseProvider.init(this)
        // 全局缺省页（loading / empty / error）：各页面不再需要自己写 emptyView / errorView
        StateLayoutDefaults.init()
        NetworkClient.initialize(
            NetworkConfig(
                baseUrl = BuildConfig.BASE_URL,
                tokenProvider = { SessionStore.accessToken },
                commonHeaders = { mapOf("Accept" to "application/json") },
                debugLogging = BuildConfig.DEBUG,
            ),
        )
    }
}

private object SessionStore {
    // 实际项目改为加密存储，并由登录模块负责更新。
    val accessToken: String? get() = null
}
