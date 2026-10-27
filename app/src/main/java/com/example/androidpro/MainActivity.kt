package com.example.androidpro

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import com.example.androidpro.core.common.BaseViewModel
import com.example.androidpro.core.ui.BaseActivity
import com.example.androidpro.databinding.ActivityMainBinding
import com.example.androidpro.feature.home.HomeFragment

class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {
    override val viewModel: MainViewModel by viewModels()
    override val bindingInflater: (LayoutInflater) -> ActivityMainBinding =
        { inflater -> ActivityMainBinding.inflate(inflater) }

    override fun setupView(savedInstanceState: Bundle?) {
        // 本 Activity 作为 Fragment 宿主，标题栏由各 Fragment 自行提供，故隐藏 Activity 级标题栏
        hideTitleBar()
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.content.id, HomeFragment())
                .commit()
        }
    }
}

class MainViewModel : BaseViewModel()
