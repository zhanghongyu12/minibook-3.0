package com.minibook.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 全屏忙碌遮罩（UI §4 C-20）。
 *
 * 半透明 scrim 背景 + 居中 [CircularProgressIndicator] 与文案，`clickable(indication = null)`
 * 吞掉点击防穿透；[BackHandler] 吞掉系统返回。锁数据入口的 Tab 屏蔽由页面层负责。
 *
 * @param message 忙碌提示文案（调用方注入）。
 */
@Composable
fun FullScreenBusyOverlay(
    message: String,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { /* 忙碌中屏蔽返回 */ }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 全局轻提示封装（UI §4 C-21）：简单封装 [SnackbarHost]。
 *
 * @param hostState 由调用方持有（[SnackbarHostState]），组件不做状态管理。
 */
@Composable
fun MiniBookSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}
