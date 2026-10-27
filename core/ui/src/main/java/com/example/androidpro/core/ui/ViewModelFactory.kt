package com.example.androidpro.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

inline fun <reified VM : ViewModel> viewModelFactory(crossinline create: (CreationExtras) -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(VM::class.java))
            @Suppress("UNCHECKED_CAST")
            return create(extras) as T
        }
    }
