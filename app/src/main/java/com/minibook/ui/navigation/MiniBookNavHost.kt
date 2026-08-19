package com.minibook.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 根级导航宿主（ARCHITECTURE §12、TASK-018 壳）。
 *
 * TASK-021 填充 `NavHost` 导航图：顶层 `isLocked` 门控（[Destination.Lock] 唯一可达，§9）
 * + 解锁后 5 个主页面 Tab（saveState/restoreState 保留各 Tab 状态）。
 * 本期为占位壳，仅验证 Compose + Hilt 注入链路可跑通。
 */
@Composable
fun MiniBookNavHost() {
    // TASK-021 替换为 NavHost(…)。占位文本不依赖主题 token，便于 TASK-018 壳独立编译。
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "MiniBook")
    }
}
