package com.minibook.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.PeriodTrend
import com.minibook.ui.theme.TabularFigures
import com.minibook.util.MoneyFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * 柱状图（报表·趋势可选形态，UI §8.2 C-28）。
 *
 * 原生 [Canvas] 自绘，`drawRoundRect`，多系列收支柱（同桶内并排）。仅渲染调用方已聚合数据点，
 * **不承担任何算术**（DEC-011）。Y 轴金额分→元经 [com.minibook.util.MoneyFormatter.formatFen]，
 * 配合 [TabularFigures] 等宽数字。
 *
 * 点击交互（A2「点击看数值」，对齐折线图）：点击命中某桶后高亮该桶的收/支柱（surface 描边环 +
 * outline 细描边）并在顶部 tooltip 显示该桶「收入 / 支出」金额；点出 plot 区域或越界即清空选中。
 * 选中态仅为表示层（桶下标），不参与任何算术（DEC-011）。
 *
 * 降级：0 数据点只画空坐标系不崩；1 个数据桶只画单组柱。
 *
 * @param buckets X 轴时间桶显示标签（与各系列 valuesFen 索引对齐、等距）。
 * @param series 要绘制的多系列；valuesFen 长度会截断到 [buckets] 长度。
 * @param height 图表高度。
 * @param onTooltipClick 非空时点击有数据的桶（即显示 tooltip 的桶）回调其下标，
 *   用于「点击数据框跳转明细」；空桶只选中不高亮跳转。null 仅选中显示 tooltip。
 */
@Composable
fun BarChart(
    buckets: List<String>,
    series: List<BarChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    onTooltipClick: ((Int) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipText = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TabularFigures)

    val pointCount = buckets.size
    val clamped = series.map { it.copy(valuesFen = it.valuesFen.take(pointCount)) }
    val allValues = clamped.flatMap { it.valuesFen }
    val axis = remember(allValues) { buildYAxis(allValues) }

    // 点击选中的桶下标（A2「点击看数值」）；null = 未选中，点出 plot 区域/越界即清空。
    var selectedIndex by remember(buckets, series) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(buckets, series) {
                detectTapGestures { offset ->
                    val hit = hitTestBarBucket(offset, buckets.size)
                    selectedIndex = hit
                    // 点有数据的桶即视为「点击数据框」：选中并回调跳转明细；空桶仅选中不高亮跳转。
                    if (hit != null && onTooltipClick != null && clamped.hasDataAt(hit)) {
                        onTooltipClick(hit)
                    }
                }
            },
    ) {
        val plot = plotArea()
        drawYGridAndLabels(axis, plot, textMeasurer, labelStyle, gridColor, zeroColor, textColor)
        drawXLabels(buckets, plot, textMeasurer, labelStyle, textColor)
        drawBars(clamped, axis, plot, pointCount, selectedIndex, tooltipBg, zeroColor)
        selectedIndex?.let { index ->
            if (index in buckets.indices) {
                drawBarSelectionOverlay(
                    index = index,
                    clamped = clamped,
                    buckets = buckets,
                    plot = plot,
                    textMeasurer = textMeasurer,
                    labelStyle = labelStyle,
                    referenceColor = gridColor,
                    tooltipBg = tooltipBg,
                    tooltipText = tooltipText,
                    tooltipBorder = gridColor,
                )
            }
        }
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

/**
 * 柱状图命中映射（纯函数，便于单测）：点击 x（px）落在 `[plotLeft, plotLeft + plotWidth]` 内时，
 * 按 `plotWidth / bucketCount` 等宽分桶，反解 `floor((x - plotLeft) / slot)` 得桶下标；
 * x 越界或下标越界 → null。
 */
internal fun barBucketIndexFromX(x: Float, plotLeft: Float, plotWidth: Float, bucketCount: Int): Int? {
    if (bucketCount <= 0 || plotWidth <= 0f) return null
    if (x < plotLeft || x > plotLeft + plotWidth) return null
    val slot = plotWidth / bucketCount
    val index = floor((x - plotLeft) / slot).toInt()
    return if (index in 0 until bucketCount) index else null
}

/**
 * 点击命中检测（A2）：用 [PointerInputScope.size] 复刻 [plotArea] 的 px 平面
 * （left=46dp/right=size.width-10dp/top=10dp/bottom=size.height-28dp）；柱状图等宽分桶，
 * x 直接 [barBucketIndexFromX] 反解桶下标；点出 plot 区域或越界 → null（清空选中）。
 */
private fun PointerInputScope.hitTestBarBucket(offset: Offset, bucketCount: Int): Int? {
    if (bucketCount <= 0) return null
    val left = 46.dp.toPx()
    val right = size.width.toFloat() - 10.dp.toPx()
    val top = 10.dp.toPx()
    val bottom = size.height.toFloat() - 28.dp.toPx()
    if (offset.y < top || offset.y > bottom) return null
    return barBucketIndexFromX(offset.x, left, (right - left).coerceAtLeast(1f), bucketCount)
}

/**
 * 某桶是否存在非零数据（任一系列该桶金额 ≠ 0）。「点击数据框跳转」仅对真实存在明细的桶触发，
 * 避免跳到无交易的空白日期。
 */
private fun List<BarChartSeries>.hasDataAt(index: Int): Boolean =
    any { (it.valuesFen.getOrNull(index) ?: 0L) != 0L }

private fun DrawScope.drawBars(
    series: List<BarChartSeries>,
    axis: YAxis,
    plot: PlotArea,
    bucketCount: Int,
    selectedIndex: Int?,
    highlightRingColor: Color,
    highlightBorderColor: Color,
) {
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
            if (bucket == selectedIndex) {
                // 选中桶强调：surface 描边环（对齐折线图选中点 surface 环）+ outline 细描边。
                drawRoundRect(
                    color = highlightRingColor,
                    topLeft = Offset(barX, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawRoundRect(
                    color = highlightBorderColor,
                    topLeft = Offset(barX, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 柱状图选中态叠加层（A2）：竖参考线（outlineVariant）+ 顶部 tooltip（首行桶标签，
 * 其后每系列「label: ¥金额」，[MoneyFormatter] + 等宽数字，对齐折线图）。柱体高亮见 [drawBars]。
 */
private fun DrawScope.drawBarSelectionOverlay(
    index: Int,
    clamped: List<BarChartSeries>,
    buckets: List<String>,
    plot: PlotArea,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    referenceColor: Color,
    tooltipBg: Color,
    tooltipText: Color,
    tooltipBorder: Color,
) {
    val x = xCenterFor(index, buckets.size, plot)

    // 竖参考线（outlineVariant），对齐折线图选中强调。
    drawLine(referenceColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())

    // tooltip 文本行：首行桶标签，其后逐系列「label: ¥金额」。
    val lines = mutableListOf(buckets.getOrNull(index).orEmpty())
    clamped.forEach { s ->
        val v = s.valuesFen.getOrNull(index) ?: return@forEach
        lines += "${s.label}: ${MoneyFormatter.formatFen(v)}"
    }
    drawBarTooltip(lines, Offset(x, plot.top), plot, textMeasurer, labelStyle, tooltipBg, tooltipText, tooltipBorder)
}

/** 轻量 tooltip（对齐折线图 drawTooltip 手法）：浅色圆角底 + 多行文本，定位锚点上方并水平钳在 plot 内。 */
private fun DrawScope.drawBarTooltip(
    lines: List<String>,
    anchor: Offset,
    plot: PlotArea,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    bg: Color,
    textColor: Color,
    border: Color,
) {
    if (lines.isEmpty()) return
    val textStyle = style.copy(color = textColor)
    val layouts = lines.map { textMeasurer.measure(it, textStyle) }
    val lineHeight = layouts.maxOfOrNull { it.size.height } ?: return
    val maxWidth = layouts.maxOfOrNull { it.size.width } ?: return
    val padH = 6.dp.toPx()
    val padV = 4.dp.toPx()
    val width = maxWidth + padH * 2
    val height = lineHeight * lines.size + padV * 2
    val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

    val x = (anchor.x - width / 2f).coerceIn(plot.left, (plot.right - width).coerceAtLeast(plot.left))
    val y = plot.top + 4.dp.toPx()

    drawRoundRect(color = bg, topLeft = Offset(x, y), size = Size(width, height), cornerRadius = corner)
    drawRoundRect(
        color = border,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    var cursorY = y + padV
    layouts.forEachIndexed { i, _ ->
        drawText(textMeasurer, lines[i], Offset(x + padH, cursorY), textStyle)
        cursorY += lineHeight
    }
}