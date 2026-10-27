package com.example.androidpro.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    protected fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _messages.emit(error.message ?: "请求失败，请稍后重试")
        }
    }
}
