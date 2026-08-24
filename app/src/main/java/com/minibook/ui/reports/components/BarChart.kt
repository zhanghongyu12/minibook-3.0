package com.minibook.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.PeriodTrend
import com.minibook.ui.theme.TabularFigures
import kotlin.math.abs
import kotlin.math.min

/**
 * 柱状图（报表·趋势可选形态，UI §8.2 C-28）。
 *
 * 原生 [Canvas] 自绘，`drawRoundRect`，多系列收支柱（同桶内并排）。仅渲染调用方已聚合数据点，
 * **不承担任何算术**（DEC-011）。Y 轴金额分→元经 [com.minibook.util.MoneyFormatter.formatFen]，
 * 配合 [TabularFigures] 等宽数字。
 *
 * 降级：0 数据点只画空坐标系不崩；1 个数据桶只画单组柱。
 *
 * @param buckets X 轴时间桶显示标签（与各系列 valuesFen 索引对齐、等距）。
 * @param series 要绘制的多系列；valuesFen 长度会截断到 [buckets] 长度。
 * @param height 图表高度。
 */
@Composable
fun BarChart(
    buckets: List<String>,
    series: List<BarChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TabularFigures)

    val pointCount = buckets.size
    val clamped = series.map { it.copy(valuesFen = it.valuesFen.take(pointCount)) }
    val allValues = clamped.flatMap { it.valuesFen }
    val axis = remember(allValues) { buildYAxis(allValues) }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val plot = plotArea()
        drawYGridAndLabels(axis, plot, textMeasurer, labelStyle, gridColor, zeroColor, textColor)
        drawXLabels(buckets, plot, textMeasurer, labelStyle, textColor)
        drawBars(clamped, axis, plot, pointCount)
    }
}

/** 单系列定义：颜色 + 逐桶金额（分）。 */
data class BarChartSeries(
    val label: String,
    val color: Color,
    val valuesFen: List<Long>,
)

/** [List<PeriodTrend>] → 收入柱系列（分）。 */
fun List<PeriodTrend>.toIncomeBarSeries(color: Color): BarChartSeries =
    BarChartSeries(label = "收入", color = color, valuesFen = incomeFenValues())

/** [List<PeriodTrend>] → 支出柱系列（分）。 */
fun List<PeriodTrend>.toExpenseBarSeries(color: Color): BarChartSeries =
    BarChartSeries(label = "支出", color = color, valuesFen = expenseFenValues())

private fun DrawScope.drawBars(series: List<BarChartSeries>, axis: YAxis, plot: PlotArea, bucketCount: Int) {
    if (series.isEmpty() || bucketCount <= 0) return

    val baseline = yFor(0L, axis, plot)
    val slot = plot.width / bucketCount
    val groupWidth = slot * 0.7f
    val barWidth = (groupWidth / series.size).coerceAtLeast(1f)
    val corner = CornerRadius(4.dp.toPx())

    repeat(bucketCount) { bucket ->
        val groupStart = plot.left + bucket * slot + (slot - groupWidth) / 2f
        series.forEachIndexed { j, ser ->
            val value = ser.valuesFen.getOrNull(bucket) ?: return@forEachIndexed
            val yTop = yFor(value, axis, plot)
            val top = min(yTop, baseline)
            val barHeight = abs(yTop - baseline).coerceAtLeast(1f)
            val barX = groupStart + j * barWidth
            drawRoundRect(
                color = ser.color,
                topLeft = Offset(barX, top),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}
