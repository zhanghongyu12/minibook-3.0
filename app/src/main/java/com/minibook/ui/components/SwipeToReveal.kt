package com.minibook.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左滑露出删除按钮容器（reveal 模式，非滑动即删）。
 *
 * 前景 [content] 可左滑（end→start）露出右侧红底「删除」按钮；点击该按钮触发 [onDeleteClick]
 * （删除不可恢复，由调用方做强确认弹窗后执行，本容器不自动删）。右滑（start→end）从关闭态
 * 无效果、从展开态仅回弹关闭。手势仅横向识别，不干扰宿主 `LazyColumn` 纵向滚动。
 *
 * 删除按钮经 `matchParentSize` 覆盖区精确等高：外层 Box 高度由前景 [content] 决定，
 * 覆盖区与其等高、内部红底右对齐固定宽 88dp，红底上下边缘与账单框严格齐平。
 *
 * 单实例展开：任意时刻至多一行展开。本行滑到展开态即回调 [onExpandRequest] 通知外部标记；
 * 外部将 [expanded] 仅置 true 于当前行，其余行收 false 经 [LaunchedEffect] 自动回收到关闭态。
 *
 * @param onDeleteClick 露出后「删除」按钮点击回调（恒为「请求删除」）。
 * @param expanded 本行当前是否被标记为展开（外部单实例状态；false 且未关闭时自动回收）。
 * @param onExpandRequest 本行滑动到展开态时通知外部（用于单实例展开的标记切换）。
 * @param content 可滑动的列表行。
 */
@Composable
fun SwipeToRevealContainer(
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandRequest: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val actionWidth = 88.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnExpandRequest = rememberUpdatedState(onExpandRequest)

    // 外部标记本行非展开时自动回收到关闭态（单实例展开：同时至多一行展开）。
    LaunchedEffect(expanded) {
        if (!expanded && offsetX.value != 0f) {
            offsetX.animateTo(0f)
        }
    }

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        // 背景删除按钮：matchParentSize 覆盖区与前景 content 严格等高（前景决定外层 Box 高度），
        // 内部红底右对齐固定宽 actionWidth，红底上下边缘与账单框齐平。
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidth)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "删除",
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // 前景：不透明 surface 层遮住背景删除按钮，且置于 `.offset` 之后、随偏移整体左移
        // （默认 offset=0 完全隐藏红底；左滑时一起左移露出删除按钮，reveal 交互正常）。
        // 仅横向可拖，偏移钳制在 [-actionWidth, 0]（只允许左滑露出，右滑要么无效果要么回弹关闭）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target =
                                    if (offsetX.value < -actionWidthPx / 2f) -actionWidthPx else 0f
                                offsetX.animateTo(target)
                                if (target < 0f) currentOnExpandRequest.value()
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                    ) { change, dragAmount ->
                        change.consume()
                        val next = (offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f)
                        scope.launch { offsetX.snapTo(next) }
                    }
                },
        ) {
            content()
        }
    }
}