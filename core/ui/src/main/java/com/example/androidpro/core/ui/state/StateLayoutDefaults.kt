package com.example.androidpro.core.ui.state

import com.drake.statelayout.StateConfig
import com.example.androidpro.core.ui.R

/**
 * 缺省页（StateLayout）的全局默认配置。
 *
 * 全项目共用同一套 loading / empty / error 布局：
 * - 业务页面**无需**再自己写 emptyView / errorView / retryView；
 * - 也无需在 XML 里重复声明 app:empty_layout 等属性；
 * - 只要在 Application.onCreate 里调一次 [init] 即可全站生效。
 *
 * 如需某个页面特殊化，仍可在该页面的 StateLayout 上单独覆盖
 * （emptyLayout / errorLayout / loadingLayout），局部配置优先于全局。
 */
object StateLayoutDefaults {

    /** 空 / 错误页中"提示文案"的控件 id，框架显示对应状态时会自动填充文案 */
    val messageId: Int = R.id.state_msg

    /** 空 / 错误页中"重试按钮"的控件 id，点击后触发重新加载 */
    val retryId: Int = R.id.state_retry

    fun init() {
        StateConfig.apply {
            emptyLayout = R.layout.layout_state_empty
            errorLayout = R.layout.layout_state_error
            loadingLayout = R.layout.layout_state_loading
            // 这里刻意不调用 setRetryIds：`setRetryIds` 只是让点击切换到 loading 态，
            // 并不会真正重新发起请求；且它设置的点击监听会与调用方的手动监听互相覆盖。
            // 列表页的"点击重试 -> 真的重新加载"由 BaseListFragment 手动接管。
        }
    }
}
