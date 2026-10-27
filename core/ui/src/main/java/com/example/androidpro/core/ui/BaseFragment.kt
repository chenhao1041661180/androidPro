package com.example.androidpro.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.example.androidpro.core.common.BaseViewModel
import com.example.androidpro.core.ui.databinding.FragmentBaseBinding
import com.example.androidpro.core.ui.widget.TitleBar
import kotlinx.coroutines.launch

/**
 * Fragment 基础类。
 *
 * ## 懒加载（核心能力）
 * 不同容器对"Fragment 是否对用户可见"的判定机制各不相同，本类把它们统一抽象为
 * [isVisibleToUser]，并在可见性发生真正变化时回调 [onFirstVisible] / [onVisible] / [onInvisible]：
 *
 * | 使用场景                         | 判定来源                                  |
 * |----------------------------------|-------------------------------------------|
 * | ViewPager（旧版 FragmentPagerAdapter） | [setUserVisibleHint]                      |
 * | ViewPager2（FragmentStateAdapter）     | 生命周期 [onResume]/[onPause]（setMaxLifecycle） |
 * | FragmentTransaction 的 hide/show      | [onHiddenChanged]                         |
 * | replace / Activity 前后台切换          | [onResume]/[onPause]                      |
 *
 * ## 三个生命周期钩子
 * - [onFirstVisible]：Fragment **首次**对用户可见时回调一次（同一份 View 生命周期内只触发一次），
 *   适合"进入页面才请求首屏数据"，避免 ViewPager 预创建时提前发请求。
 * - [onVisible]：Fragment **每次**对用户可见时都会回调（首次可见时也会触发，且一定晚于 [onFirstVisible]），
 *   适合"每次回到页面都刷新"的场景。
 * - [onInvisible]：由可见变为不可见时回调。
 *
 * 分发做了去重（基于当前可见状态位），同一状态反复触发不会重复回调；同时兼容嵌套 Fragment：
 * 父 Fragment 不可见时，子 Fragment 也会判定为不可见。
 */
abstract class BaseFragment<VB : ViewBinding, VM : ViewModel> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB get() = checkNotNull(_binding)

    private var _baseBinding: FragmentBaseBinding? = null
    protected val baseBinding: FragmentBaseBinding get() = checkNotNull(_baseBinding)

    protected abstract val viewModel: VM
    protected abstract val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> VB

    // ---- 懒加载内部状态 ----
    private var isViewPrepared = false   // onViewCreated 已完成
    private var isFirstVisible = true    // 是否还处在"首次可见"待触发状态
    private var hasLoadedOnce = false    // onFirstVisible 是否已执行过（去重）
    private var isCurrentVisible = false // 当前聚合可见状态，用于去重分发

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _baseBinding = FragmentBaseBinding.inflate(inflater, container, false)
        val child = bindingInflater(inflater, _baseBinding!!.contentContainer, false).also { _binding = it }
        _baseBinding!!.contentContainer.addView(child.root)
        return _baseBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewPrepared = true
        setupView(savedInstanceState)
        observeState()
        observeBaseMessages()
        loadData(savedInstanceState)
        dispatchVisibility() // 兜底：极端情况下此时已可见（一般不会，因为 onResume 后才 isResumed）
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }
    // ===== 子类重写区（eager，在 onViewCreated 中同步调用）=====
    protected open fun setupView(savedInstanceState: Bundle?) = Unit
    protected open fun observeState() = Unit
    protected open fun loadData(savedInstanceState: Bundle?) = Unit

    // ===== 懒加载钩子（仅在可见性变化时触发）=====
    /**
     * 懒加载：仅在 Fragment **首次**对用户可见时回调一次（同一份 View 生命周期内只触发一次）。
     * View 被销毁重建（如 ViewPager 超出缓存、replace、横竖屏旋转）后会按"首次"重新触发。
     */
    protected open fun onFirstVisible() = Unit

    /**
     * 每次 Fragment 对用户可见时回调（首次可见也会触发，且晚于 [onFirstVisible]）。
     * 也包含从后台返回前台的场景。适合每次回到页面都刷新的需求。
     */
    protected open fun onVisible() = Unit

    /** Fragment 由可见变为不可见时回调。 */
    protected open fun onInvisible() = Unit

    /** 当前是否对用户可见（已综合 ViewPager / 显示隐藏 / 生命周期 / 父 Fragment）。 */
    val isVisibleToUser: Boolean get() = computeVisible()

    // ===== TitleBar 便捷封装（base 布局自带，子类无需关心其内部结构）=====
    /** 显示标题栏（默认即显示）。 */
    protected fun showTitleBar() { baseBinding.titleBar.visibility = View.VISIBLE }
    /** 隐藏标题栏（如全屏 / 无标题页面）。 */
    protected fun hideTitleBar() { baseBinding.titleBar.visibility = View.GONE }
    /** 设置中间标题文案（null / 空串隐藏）。 */
    protected fun setTitleBar(title: CharSequence?) { baseBinding.titleBar.setTitle(title) }
    /** 直接拿到 TitleBar 实例，调用完整 API（showBack / addRightIcon / setCenterView / setRightView 等）。 */
    protected val titleBar: TitleBar get() = baseBinding.titleBar

    // ===== 可见性计算与分发 =====
    private fun computeVisible(): Boolean {
        // 任一条件不满足即不可见：视图未就绪 / 被 hide / 未 resume / 旧版 ViewPager 标记不可见
        if (!isViewPrepared || isHidden || !isResumed || !userVisibleHint) return false
        // 嵌套 Fragment：父不可见则子不可见
        val parent = parentFragment
        if (parent != null) {
            val parentVisible = if (parent is BaseFragment<*, *>) parent.isVisibleToUser else parent.isVisible
            return parentVisible
        }
        return true
    }

    private fun dispatchVisibility() {
        val now = computeVisible()
        if (now == isCurrentVisible) return // 状态未变，去重
        isCurrentVisible = now
        if (now) {
            if (isFirstVisible && !hasLoadedOnce) {
                isFirstVisible = false
                hasLoadedOnce = true
                onFirstVisible()
            }
            onVisible()
        } else {
            onInvisible()
        }
    }

    override fun onResume() {
        super.onResume()
        dispatchVisibility()
    }

    override fun onPause() {
        super.onPause()
        dispatchVisibility()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        dispatchVisibility()
    }

    /**
     * 兼容旧版 ViewPager（FragmentPagerAdapter / FragmentStatePagerAdapter 的
     * BEHAVIOR_SET_USER_VISIBLE_HINT）。ViewPager2 走生命周期，不会调用此方法。
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        "仅用于兼容旧版 ViewPager；ViewPager2 使用生命周期判定可见性，无需重写此方法。",
        ReplaceWith("onVisible() / onInvisible()")
    )
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isViewPrepared) dispatchVisibility()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _baseBinding = null
        isViewPrepared = false
        isCurrentVisible = false
        // 视图销毁后重置首次标记，下次重建 View 时仍按"首次可见"重新懒加载
        isFirstVisible = true
        hasLoadedOnce = false
    }

    private fun observeBaseMessages() {
        val base = viewModel as? BaseViewModel ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                base.messages.collect { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
