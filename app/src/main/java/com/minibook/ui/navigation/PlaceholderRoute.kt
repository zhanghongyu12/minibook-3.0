package com.minibook.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 占位页面（TASK-021）。
 *
 * 简单居中显示 [label]，仅供导航图中仍注册为占位的 sheets 家族目的地使用（搜索/报表/预算等
 * Phase 2 页面均已落地真实页面 composable，不再经本占位；本占位随 sheets 占位继续保留）。
 *
 * @param label 占位文案（通常为页面名，或「页面名 + 说明」）。
 * @param modifier 外层修饰符；默认 `fillMaxSize()` 居中。
 */
@Composable
fun PlaceholderRoute(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
