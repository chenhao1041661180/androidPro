package com.example.androidpro.core.ui.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.androidpro.core.ui.R
import com.example.androidpro.core.ui.databinding.TitleBarBinding

/**
 * 通用标题栏（自定义 View）。
 *
 * 采用三段式布局：左侧（返回图标 + 可选文字）、中间（标题文案 + 左右小图标，或插入自定义 View）、
 * 右侧（1~2 个图标 + 一个自定义 View）。
 *
 * 沉浸式适配：TitleBar 自动将 `paddingTop` 设为状态栏高度，并监听 WindowInsets 校正，
 * 因此**调用方无需手动处理状态栏沉浸式**，标题栏背景会自然延伸到状态栏区域。
 *
 * ## 支持的自定义属性（均可在 XML 中通过 app:tb_* 配置，也都有对应的运行时方法）
 * | attr | 运行时方法 | 说明 |
 * |------|-----------|------|
 * | tb_background | setTitleBarBackgroundColor / setTitleBarBackgroundResource | 标题栏整体背景 |
 * | tb_height | setTitleBarHeightDp / setTitleBarHeightPx | 标题栏「内容区」高度（不含状态栏），默认 48dp；组件内部自动叠加状态栏高度 |
 * | tb_backIcon | setBackIcon @setLeftIcon | 返回图标资源 |
 * | tb_backIconSize | setBackIconSizeDp / setBackIconSize | 返回图标尺寸 |
 * | tb_title | setTitle | 标题文案 |
 * | tb_titleTextSize | setTitleTextSizeSp / setTitleTextSizePx | 标题字号 |
 * | tb_titleTextColor | setTitleTextColor | 标题颜色 |
 * | tb_rightIcon | setRightIcon | 右侧图标资源（第一槽） |
 * | tb_rightIconSize | setRightIconSizeDp / setRightIconSize | 右侧图标尺寸 |
 *
 * 典型用法：
 * ```
 * titleBar.showBack { activity?.onBackPressed() }   // 显示返回箭头，默认点击关闭页面
 * titleBar.setTitle("首页")
 * titleBar.addRightIcon(R.drawable.ic_search) { /* ... */ }
 * titleBar.setCenterView(tabLayout)                 // 中间插入 Tab，隐藏标题文案
 * titleBar.setRightView(customButton)               // 右侧插入自定义 View
 * ```
 */
class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 标题栏「内容区」目标高度（不含状态栏），默认 48dp，可由 tb_height 覆盖。 */
    private var contentHeight: Int = 0
    /** 当前状态栏高度（dimen 兜底 + WindowInsets 校正）。 */
    private var statusBarHeight: Int = 0

    private val vb: TitleBarBinding

    private var centerView: View? = null
    private var rightView: View? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // title_bar.xml 根节点是 <merge>，且本类本身就是 ViewGroup，
        // 因此必须 attachToParent = true（第三个参数）。传 false 时 LayoutInflater 会直接抛：
        // InflateException: <merge /> can be used only with a valid ViewGroup root and attachToRoot=true
        vb = TitleBarBinding.inflate(LayoutInflater.from(context), this, true)
        contentHeight = dp2px(50)        // 默认内容高度 48dp
        readAttrs(attrs, defStyleAttr)  // 先解析 tb_height（可能覆盖默认）
        applyStatusBarPadding()         // 再 补 状态栏 padding + 设定最小高度
    }

    // ===== 属性解析（XML app:tb_* 与默认行为统一收敛到这里）=====
    private fun readAttrs(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        val a = context.obtainStyledAttributes(attrs, R.styleable.TitleBar, defStyleAttr, 0)
        try {
            // 整体背景色
            if (a.hasValue(R.styleable.TitleBar_tb_background)) {
                setBackgroundColor(a.getColor(R.styleable.TitleBar_tb_background, 0))
            }
            // 标题栏内容高度（不含状态栏），默认 48dp，可覆盖
            if (a.hasValue(R.styleable.TitleBar_tb_height)) {
                contentHeight = a.getDimensionPixelSize(R.styleable.TitleBar_tb_height, contentHeight)
            }
            // 左侧返回图标（设置后可见 + 默认点击关闭页面）
            if (a.hasValue(R.styleable.TitleBar_tb_backIcon)) {
                val res = a.getResourceId(R.styleable.TitleBar_tb_backIcon, 0)
                vb.ivLeftIcon.apply {
                    visibility = VISIBLE
                    setImageResource(res)
                    setOnClickListener { (context as? Activity)?.onBackPressed() }
                }
            }
            // 左侧返回图标尺寸
            if (a.hasValue(R.styleable.TitleBar_tb_backIconSize)) {
                setIconSize(vb.ivLeftIcon, a.getDimensionPixelSize(R.styleable.TitleBar_tb_backIconSize, 0))
            }
            // 标题文案
            if (a.hasValue(R.styleable.TitleBar_tb_title)) {
                setTitle(a.getText(R.styleable.TitleBar_tb_title))
            }
            // 标题字号
            if (a.hasValue(R.styleable.TitleBar_tb_titleTextSize)) {
                vb.tvTitle.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    a.getDimensionPixelSize(R.styleable.TitleBar_tb_titleTextSize, 0).toFloat()
                )
            }
            // 标题颜色
            if (a.hasValue(R.styleable.TitleBar_tb_titleTextColor)) {
                vb.tvTitle.setTextColor(a.getColor(R.styleable.TitleBar_tb_titleTextColor, 0))
            }
            // 右侧图标（第一槽）
            if (a.hasValue(R.styleable.TitleBar_tb_rightIcon)) {
                val res = a.getResourceId(R.styleable.TitleBar_tb_rightIcon, 0)
                vb.ivRight1.apply { visibility = VISIBLE; setImageResource(res) }
            }
            // 右侧图标尺寸（两个槽位一起改）
            if (a.hasValue(R.styleable.TitleBar_tb_rightIconSize)) {
                val size = a.getDimensionPixelSize(R.styleable.TitleBar_tb_rightIconSize, 0)
                setIconSize(vb.ivRight1, size)
                setIconSize(vb.ivRight2, size)
            }
        } finally {
            a.recycle()
        }
    }

    // ===== 左侧 =====
    /** 显示返回箭头；默认点击关闭当前页面，可传自定义 onClick 覆盖。 */
    fun showBack(onClick: (View) -> Unit = { (context as? Activity)?.onBackPressed() }) {
        showBack(R.drawable.ic_back, onClick)
    }

    /** 自定义左侧图标（替换返回箭头）。 */
    fun showBack(@DrawableRes resId: Int, onClick: (View) -> Unit) {
        vb.ivLeftIcon.apply {
            visibility = VISIBLE
            setImageResource(resId)
            setOnClickListener { onClick(it) }
        }
    }

    /** 左侧文字（如「返回」），不需要时传 null 隐藏。 */
    fun setLeftText(text: CharSequence?, onClick: ((View) -> Unit)? = null) {
        vb.tvLeftText.apply {
            visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
            this.text = text
            setOnClickListener { onClick?.invoke(it) }
        }
    }

    // ===== 中间 =====
    /** 标题文案；传 null/空串隐藏。 */
    fun setTitle(title: CharSequence?) {
        vb.tvTitle.visibility = if (title.isNullOrEmpty()) GONE else VISIBLE
        vb.tvTitle.text = title
    }

    /** 标题左侧小图标（如筛选箭头）。 */
    fun setTitleLeftIcon(@DrawableRes resId: Int) {
        vb.ivTitleLeft.apply { visibility = VISIBLE; setImageResource(resId) }
    }

    /** 标题右侧小图标（如下拉箭头）。 */
    fun setTitleRightIcon(@DrawableRes resId: Int) {
        vb.ivTitleRight.apply { visibility = VISIBLE; setImageResource(resId) }
    }

    /**
     * 在中间区域插入自定义 View（如 Tab 切换条），插入后隐藏标题文案；
     * 传 null 恢复标题文案。
     */
    fun setCenterView(view: View?) {
        if (view == null) {
            clearCenterView()
            return
        }
        centerView?.let { vb.centerContainer.removeView(it) }
        centerView = view
        vb.titleWrapper.visibility = GONE
        // 与 titleWrapper 行为一致：在 centerContainer 中水平居中（容器本身已相对整条 bar 居中）
        vb.centerContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
    }

    fun clearCenterView() {
        centerView?.let { vb.centerContainer.removeView(it) }
        centerView = null
        vb.titleWrapper.visibility = VISIBLE
    }

    // ===== 右侧 =====
    /** 右侧图标，最多 2 个（自动填空位）；点击回调 onClick。 */
    fun addRightIcon(@DrawableRes resId: Int, onClick: (View) -> Unit) {
        val target = if (vb.ivRight1.visibility == GONE) vb.ivRight1 else vb.ivRight2
        target.apply {
            visibility = VISIBLE
            setImageResource(resId)
            setOnClickListener { onClick(it) }
        }
    }

    /** 右侧插入自定义 View（如自定义按钮样式），重复调用会替换旧的。 */
    fun setRightView(view: View) {
        rightView?.let { vb.rightContainer.removeView(it) }
        rightView = view
        vb.rightContainer.addView(view)
    }

    // ===== attr 对应的运行时 API（供 base 容器子类在代码中配置）=====
    /** 设置标题栏整体背景色（对应 tb_background）。 */
    fun setTitleBarBackgroundColor(color: Int) = setBackgroundColor(color)

    /** 设置标题栏背景为 drawable 资源（tb_background 的延伸）。 */
    fun setTitleBarBackgroundResource(@DrawableRes resId: Int) = setBackgroundResource(resId)

    /**
     * 运行时设置标题栏「内容区」高度（dp，不含状态栏），对应 XML 属性 tb_height。
     * 常用于不同页面需要不同高度（如首页更高、二级页更矮）的场景，可覆盖 XML 默认值。
     */
    fun setTitleBarHeightDp(dp: Int) {
        contentHeight = dp2px(dp)
        updateHeightForInsets(statusBarHeight)
        requestLayout()
    }

    /** 运行时设置标题栏「内容区」高度（px）。 */
    fun setTitleBarHeightPx(px: Int) {
        contentHeight = px
        updateHeightForInsets(statusBarHeight)
        requestLayout()
    }

    /** 设置左侧返回图标资源并使其可见（保持默认「点击关闭页面」，可用 setOnBackClickListener 覆盖）。 */
    fun setBackIcon(@DrawableRes resId: Int) {
        vb.ivLeftIcon.apply { visibility = VISIBLE; setImageResource(resId) }
    }

    /** 左侧返回图标尺寸（px）。 */
    fun setBackIconSize(px: Int) = setIconSize(vb.ivLeftIcon, px)

    /** 左侧返回图标尺寸（dp，自动换算）。 */
    fun setBackIconSizeDp(dp: Int) = setBackIconSize(dp2px(dp))

    /** 自定义返回图标点击（覆盖默认关闭页面行为）。 */
    fun setOnBackClickListener(onClick: (View) -> Unit) {
        vb.ivLeftIcon.apply { visibility = VISIBLE}
        vb.ivLeftIcon.setOnClickListener { onClick(it) }
    }

    /** 标题字体大小（px）。 */
    fun setTitleTextSizePx(px: Int) =
        vb.tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, px.toFloat())

    /** 标题字体大小（sp）。 */
    fun setTitleTextSizeSp(sp: Float) =
        vb.tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)

    /** 标题字体颜色（对应 tb_titleTextColor）。 */
    fun setTitleTextColor(color: Int) = vb.tvTitle.setTextColor(color)

    /** 设置右侧第一个图标资源并使其可见（对应 tb_rightIcon）。 */
    fun setRightIcon(@DrawableRes resId: Int) {
        vb.ivRight1.apply { visibility = VISIBLE; setImageResource(resId) }
    }

    /** 右侧图标尺寸（px，作用于右侧两个槽位）。 */
    fun setRightIconSize(px: Int) {
        setIconSize(vb.ivRight1, px)
        setIconSize(vb.ivRight2, px)
    }

    /** 右侧图标尺寸（dp，自动换算）。 */
    fun setRightIconSizeDp(dp: Int) = setRightIconSize(dp2px(dp))

    /** 自定义右侧第一个图标的点击（覆盖 addRightIcon/tb_rightIcon 默认 null 行为）。 */
    fun setOnRightIconClickListener(onClick: (View) -> Unit) {
        vb.ivRight1.setOnClickListener { onClick(it) }
    }

    // ===== 沉浸式：自动补 状态栏高度 padding，并保证内容区不被挤压 =====
    private fun applyStatusBarPadding() {
        // dimen 兜底（framework 内置 status_bar_height），保证即使 insets 未派发也有合理值
        statusBarHeight = getStatusBarHeight()
        updateHeightForInsets(statusBarHeight)
        // WindowInsets 校正（更准确，适配刘海/动态隐藏）：状态栏高度变化时同步更新
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (top > 0) {
                statusBarHeight = top
                updateHeightForInsets(top)
            }
            insets
        }
    }

    /**
     * 将状态栏高度同时作用到两处，解决「用户设了 layout_height 后内容被 padding 挤扁」的问题：
     * 1) paddingTop —— 让标题栏背景延伸到状态栏区域（沉浸式），内容下沉到状态栏下方；
     * 2) minimumHeight = 内容高度 + 状态栏高度 —— 保证「内容区」始终 >= contentHeight，
     *    即便用户把 layout_height 写成固定值（如 48dp），onMeasure 也会把总高度补足。
     */
    private fun updateHeightForInsets(top: Int) {
        updatePadding(top = top)
        minimumHeight = contentHeight + top
    }

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    /**
     * 兜底：若用户给 TitleBar 写死 layout_height（如 48dp/50dp），paddingTop 会侵占内容高度，
     * 导致内容被挤压。这里把总高度补足为「内容高度 + 状态栏高度」，确保内容区 >= contentHeight。
     */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (statusBarHeight <= 0) return
        val contentH = measuredHeight - paddingTop - paddingBottom
        if (contentH < contentHeight) {
            setMeasuredDimension(measuredWidth, contentHeight + paddingTop + paddingBottom)
        }
    }

    private fun setIconSize(iv: ImageView, sizePx: Int) {
        val lp = iv.layoutParams ?: ViewGroup.LayoutParams(sizePx, sizePx)
        lp.width = sizePx
        lp.height = sizePx
        iv.layoutParams = lp
    }

    private fun dp2px(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    // ===== centerContainer 在整个 TitleBar 中居中（替代子 View 平移）=====
    // 默认 weight 布局下 centerContainer 仅占「左栏结束→右栏开始」的剩余区，
    // 其几何中心会随 左右宽 度 差而偏离整条 bar 的中点。
    // 做法：不给子 View 做 translationX，而是给 centerContainer 设「不对称外边距」，
    // 使它在整条 TitleBar 中居中；其内部子 View 由 layout_gravity/gravity 自动居中。
    // 与 titleWrapper 是否显示无关——自定义 centerView 同样受益。
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        recenterTitle()
    }

    private fun recenterTitle() {
        val barW = width
        if (barW <= 0) return
        // 左右两栏「实际占据宽度」= 内容宽度 + 自身左右外边距。
        // 注意：View.getWidth() 不含 margin，必须单独加上，否则含 margin 时计算会偏。
        val leftLp = vb.leftContainer.layoutParams as? ViewGroup.MarginLayoutParams
        val rightLp = vb.rightContainer.layoutParams as? ViewGroup.MarginLayoutParams
        val leftOcc = vb.leftContainer.width + (leftLp?.leftMargin ?: 0) + (leftLp?.rightMargin ?: 0)
        val rightOcc = vb.rightContainer.width + (rightLp?.leftMargin ?: 0) + (rightLp?.rightMargin ?: 0)
        // 水平 LinearLayout 下，centerContainer 中心 = 起点L + mL_c + C/2。
        // 令其等于整条 TitleBar 中点 barW/2，需满足 mL_c - mR_c = (rightOcc - leftOcc) + (pR - pL)：
        //   右侧更宽(diff>0) → 补左边距 diff；左侧更宽(diff<0) → 补右边距 |diff|。
        // （pR - pL 为 TitleBar 自身不对称 padding 的修正，无 padding 时该项为 0）
        val diff = (rightOcc - leftOcc) + (paddingRight - paddingLeft)
        val lp = vb.centerContainer.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val newLeft = if (diff >= 0) diff else 0
        val newRight = if (diff < 0) -diff else 0
        if (lp.leftMargin == newLeft && lp.rightMargin == newRight) return
        lp.leftMargin = newLeft
        lp.rightMargin = newRight
        vb.centerContainer.layoutParams = lp
    }
}
