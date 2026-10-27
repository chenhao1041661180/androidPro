package com.example.androidpro.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.example.androidpro.core.common.BaseViewModel
import com.example.androidpro.core.ui.databinding.ActivityBaseBinding
import com.example.androidpro.core.ui.widget.TitleBar
import kotlinx.coroutines.launch

/**
 * Activity 基础类（ViewBinding + ViewModel 双泛型）。
 *
 * 除既有的 [setupView]/[observeState]/[loadData] 钩子外，额外封装了 Fragment 事务方法，
 * 覆盖「切换 / 替换 / 显示隐藏」等常见场景，配合 [BaseFragment] 的可见性分发可实现懒加载：
 *
 * - [addFragment] / [replaceFragment]：增 / 替（默认不进回退栈，可传 addToBackStack）。
 * - [showFragment] / [hideFragment] / [removeFragment]：显示 / 隐藏 / 移除单个 Fragment。
 * - [switchFragment]：多 Fragment 同容器切换——隐藏其余、显示/添加目标，**懒加载友好**，
 *   常用于底部 Tab：被切走的 Fragment 收到 onInvisible，切回的收到 onVisible / onFirstVisible。
 *
 * 事务统一用 `commitAllowingStateLoss()`，避免在 `onSaveInstanceState` 之后调用导致的崩溃；
 * 代价是极端情况下（进程被系统回收）这次事务可能丢失，对普通的 UI 切换场景可接受。
 */
abstract class BaseActivity<VB : ViewBinding, VM : ViewModel> : AppCompatActivity() {
    private var _binding: VB? = null
    protected val binding: VB get() = checkNotNull(_binding)

    private var _baseBinding: ActivityBaseBinding? = null
    protected val baseBinding: ActivityBaseBinding get() = checkNotNull(_baseBinding)

    protected abstract val viewModel: VM
    protected abstract val bindingInflater: (LayoutInflater) -> VB

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        _baseBinding = ActivityBaseBinding.inflate(layoutInflater)
        _binding = bindingInflater(layoutInflater)
        _baseBinding!!.contentContainer.addView(binding.root)
        setContentView(_baseBinding!!.root)
        setupView(savedInstanceState)
        observeState()
        observeBaseMessages()
        loadData(savedInstanceState)
    }

    protected open fun setupView(savedInstanceState: Bundle?) = Unit
    protected open fun observeState() = Unit
    protected open fun loadData(savedInstanceState: Bundle?) = Unit

    // ===== TitleBar 便捷封装（base 布局自带，子类无需关心其内部结构）=====
    /** 显示标题栏（默认即显示）。 */
    protected fun showTitleBar() { baseBinding.titleBar.visibility = View.VISIBLE }
    /** 隐藏标题栏（如全屏 / 无标题页面）。 */
    protected fun hideTitleBar() { baseBinding.titleBar.visibility = View.GONE }
    /** 设置中间标题文案（null / 空串隐藏）。 */
    protected fun setTitleBar(title: CharSequence?) { baseBinding.titleBar.setTitle(title) }
    /** 直接拿到 TitleBar 实例，调用完整 API（showBack / addRightIcon / setCenterView / setRightView 等）。 */
    protected val titleBar: TitleBar get() = baseBinding.titleBar

    // ===== Fragment 事务封装（切换 / 替换 / 显示隐藏）=====

    /** 添加 Fragment（不指定 tag 时取类名）。 */
    protected fun addFragment(containerId: Int, fragment: Fragment, tag: String? = null, addToBackStack: Boolean = false) {
        supportFragmentManager.beginTransaction().apply {
            add(containerId, fragment, tag ?: fragment.javaClass.name)
            if (addToBackStack) addToBackStack(tag ?: fragment.javaClass.name)
            commitAllowingStateLoss()
        }
    }

    /** 替换容器中的 Fragment（旧 Fragment 会被移除）。 */
    protected fun replaceFragment(containerId: Int, fragment: Fragment, tag: String? = null, addToBackStack: Boolean = false) {
        supportFragmentManager.beginTransaction().apply {
            replace(containerId, fragment, tag ?: fragment.javaClass.name)
            if (addToBackStack) addToBackStack(tag ?: fragment.javaClass.name)
            commitAllowingStateLoss()
        }
    }

    /** 显示已添加的 Fragment（触发其 onVisible）。 */
    protected fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().show(fragment).commitAllowingStateLoss()
    }

    /** 隐藏已添加的 Fragment（触发其 onInvisible）。 */
    protected fun hideFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().hide(fragment).commitAllowingStateLoss()
    }

    /** 移除 Fragment。 */
    protected fun removeFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
    }

    /**
     * 多 Fragment 同容器切换（Tab 懒加载友好）：
     * 把 containerId 内已添加、非目标且当前可见的 Fragment 全部 hide，
     * 目标 Fragment 若已添加则 show，否则 add。
     * 配合 [BaseFragment]，被切走的 Fragment 收到 onInvisible，切回的收到 onVisible / onFirstVisible。
     */
    protected fun switchFragment(containerId: Int, fragment: Fragment, tag: String? = null) {
        val fm = supportFragmentManager
        fm.beginTransaction().apply {
            fm.fragments
                .filter { it.id == containerId && it != fragment && !it.isHidden }
                .forEach { hide(it) }
            if (fragment.isAdded) show(fragment) else add(containerId, fragment, tag ?: fragment.javaClass.name)
            commitAllowingStateLoss()
        }
    }

    private fun observeBaseMessages() {
        val base = viewModel as? BaseViewModel ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                base.messages.collect { Toast.makeText(this@BaseActivity, it, Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
