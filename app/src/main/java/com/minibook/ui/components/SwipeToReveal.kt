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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 * @param onDeleteClick 露出后「删除」按钮点击回调（恒为「请求删除」）。
 * @param content 可滑动的列表行。
 */
@Composable
fun SwipeToRevealContainer(
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actionWidth = 88.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        // 背景：右对齐红底删除按钮，左滑露出后点击触发删除。
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
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

        // 前景：仅横向可拖，偏移钳制在 [-actionWidth, 0]（只允许左滑露出，右滑要么无效果要么回弹关闭）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target =
                                    if (offsetX.value < -actionWidthPx / 2f) -actionWidthPx else 0f
                                offsetX.animateTo(target)
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