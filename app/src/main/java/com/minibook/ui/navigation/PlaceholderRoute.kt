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
 * 简单居中显示 [label]，供导航图中尚未实现的目的地（TASK-022~030 页面、sheets 家族、
 * Phase 2 占位页）使用，验证导航图可达性与依赖注入链路。落地 task 将各目的地替换为真实
 * 页面 composable 后，本占位随之退役。
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
