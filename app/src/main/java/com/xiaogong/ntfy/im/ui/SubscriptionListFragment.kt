package com.xiaogong.ntfy.im.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xiaogong.ntfy.im.R

/**
 * SubscriptionListFragment - 显示订阅列表
 * 注意：这是一个简化版本的 Fragment 骨架。
 * 需要将 MainActivity 中的订阅列表相关逻辑迁移到这里，包括：
 * - RecyclerView 和 Adapter 的初始化
 * - ViewModel 的观察
 * - 横幅（Battery、WebSocket）的处理
 * - FAB 按钮和添加订阅功能
 * - 下拉刷新功能
 * - 长按多选删除功能
 */
class SubscriptionListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_subscription_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: 从 MainActivity 迁移以下逻辑：
        // 1. 初始化 RecyclerView 和 MainAdapter
        // 2. 观察 ViewModel 的订阅列表
        // 3. 设置 FAB 按钮点击事件
        // 4. 设置下拉刷新
        // 5. 初始化横幅（Battery、WebSocket、WebSocket Reconnect）
        // 6. 处理空状态显示
    }

    companion object {
        fun newInstance() = SubscriptionListFragment()
    }
}
