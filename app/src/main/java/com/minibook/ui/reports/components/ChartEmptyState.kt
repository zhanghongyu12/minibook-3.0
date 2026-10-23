package com.minibook.ui.reports.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 图表空态（报表，UI §8.2 C-31）。
 *
 * 图标（surfaceContainerHigh 圆底）+「暂无数据」，用于某张图无任何数据时占位；单点降级由各图表自身
 * 处理（1 点画点/单柱/满圈），这里只负责空态。图标默认用内置手绘“柱状图”占位（material-icons 缺失，
 * 沿用工程手绘 ImageVector 方案），调用方可注入自己的 [ImageVector]。
 *
 * @param icon 空态图标；默认内置柱状图占位。
 * @param message 主文案，默认「暂无数据」。
 * @param subtitle 副文案（可空）。
 */
@Composable
fun ChartEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = DefaultChartEmptyIcon,
    message: String = "暂无数据",
    subtitle: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 内置空态占位图标：手绘“柱状图”（三根柱 + 基线，material-icons 缺失时的占位，TASK-020 偏差）。
 * fill 用黑色无碍（tint 由 [Icon] 统一覆盖）。internal：作为公共 [ChartEmptyState] 的默认参数值，
 * 必须在调用方可见（Kotlin 默认参数在调用点内联求值）。
 */
internal val DefaultChartEmptyIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChartEmpty",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            // 柱 1（短）
            moveTo(5f, 20f)
            lineTo(5f, 14f)
            lineTo(8f, 14f)
            lineTo(8f, 20f)
            close()
            // 柱 2（中）
            moveTo(11f, 20f)
            lineTo(11f, 9f)
            lineTo(14f, 9f)
            lineTo(14f, 20f)
            close()
            // 柱 3（长）
            moveTo(17f, 20f)
            lineTo(17f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 20f)
            close()
            // 基线
            moveTo(3f, 20.5f)
            lineTo(3f, 19.5f)
            lineTo(21f, 19.5f)
            lineTo(21f, 20.5f)
            close()
        }
    }.build()
}
