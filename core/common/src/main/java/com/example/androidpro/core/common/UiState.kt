package com.example.androidpro.core.common

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Empty(val message: String? = null) : UiState<Nothing>
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>
}
