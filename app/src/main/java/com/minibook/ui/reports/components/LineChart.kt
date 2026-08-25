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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** 单条折线的数据点标记上限；超过只画线不画点，避免视觉噪音。 */
private const val MAX_MARKER_POINTS = 24

/**
 * 折线图（报表·趋势，UI §8.2 C-27）。
 *
 * 原生 [Canvas] 自绘，多系列（收入 / 支出 / 结余），仅渲染调用方已聚合的数据点，
 * **不承担任何算术**（DEC-011）。X 轴为等距时间桶（月视图按天 / 年视图按月），
 * Y 轴金额分→元经 [com.minibook.util.MoneyFormatter.formatFen]，配合 [TabularFigures] 等宽数字。
 *
 * 降级：0 数据点只画空坐标系不崩（空态文案由 [ChartEmptyState] 呈现）；1 个数据点只画单点，
 * 不画无意义连线。
 *
 * @param buckets X 轴时间桶显示标签（与各系列 valuesFen 索引对齐、等距）。
 * @param series 要绘制的多系列；valuesFen 长度会截断到 [buckets] 长度。
 * @param height 图表高度。
 * @param showPoints 是否绘制数据点标记（点数超过 [MAX_MARKER_POINTS] 时自动忽略）。
 */
@Composable
fun LineChart(
    buckets: List<String>,
    series: List<LineChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    showPoints: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipText = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TabularFigures)

    // 各系列值截断到桶数，避免数据点与“水桶”索引错位。
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
                    selectedIndex = hitTestBucket(offset, buckets.size)
                }
            },
    ) {
        val plot = plotArea()
        drawYGridAndLabels(axis, plot, textMeasurer, labelStyle, gridColor, zeroColor, textColor)
        drawXLabels(buckets, plot, textMeasurer, labelStyle, textColor)
        clamped.forEach { drawSeries(it, axis, plot, showPoints, pointCount) }
        selectedIndex?.let { index ->
            if (index in buckets.indices) {
                drawSelectionOverlay(
                    index = index,
                    axis = axis,
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
data class LineChartSeries(
    val label: String,
    val color: Color,
    val valuesFen: List<Long>,
    /** 逐点语义色（如结余正负取收入/支出色）；null 时整条统一用 [color]。 */
    val colorForValue: ((fen: Long) -> Color)? = null,
)

/** [List<PeriodTrend>] → X 轴桶标签（等距时间桶）。 */
fun List<PeriodTrend>.bucketLabels(): List<String> = map { it.bucket }

/** [List<PeriodTrend>] → 收入序列（分）。 */
fun List<PeriodTrend>.incomeFenValues(): List<Long> = map { it.incomeFen }

/** [List<PeriodTrend>] → 支出序列（分）。 */
fun List<PeriodTrend>.expenseFenValues(): List<Long> = map { it.expenseFen }

/** [List<PeriodTrend>] → 结余序列（分）。 */
fun List<PeriodTrend>.balanceFenValues(): List<Long> = map { it.balanceFen }

// ---------------------------------------------------------------------------
// Y 轴刻度
// ---------------------------------------------------------------------------

/** Y 轴范围与刻度（单位分）。 */
internal data class YAxis(val minFen: Long, val maxFen: Long, val ticks: List<Long>)

internal fun buildYAxis(valuesFen: List<Long>): YAxis {
    val dataMin = valuesFen.minOrNull() ?: 0L
    val dataMax = valuesFen.maxOrNull() ?: 0L
    val lower = minOf(0L, dataMin)
    val upper = maxOf(0L, dataMax)
    // 全部为 0：给一个 ¥100 的示意轴，避免除零/扁平轴。
    if (lower == 0L && upper == 0L) {
        val top = 10_000L
        return YAxis(0L, top, tickRange(0L, top))
    }
    val step = niceStep(upper - lower, 4)
    val start = floorDiv(lower, step) * step
    val end = ceilDiv(upper, step) * step
    val ticks = generateSequence(start) { it + step }.takeWhile { it <= end }.toList().ifEmpty { listOf(0L) }
    return YAxis(ticks.first(), ticks.last(), ticks)
}

private fun tickRange(lo: Long, hi: Long): List<Long> {
    if (lo == hi) return listOf(lo)
    val step = niceStep(hi - lo, 4)
    return generateSequence(lo) { it + step }.takeWhile { it <= hi }.toList()
}

/** 取“好看”的步长：1/2/5 × 10^k，使刻度目标约 4 段。 */
internal fun niceStep(range: Long, targetTicks: Int): Long {
    if (range <= 0L) return 1L
    val rough = range.toDouble() / targetTicks
    if (rough <= 0.0) return 1L
    val mag = 10.0.pow(floor(log10(rough)))
    val norm = rough / mag
    val nice = when {
        norm < 1.5 -> 1.0
        norm < 3.0 -> 2.0
        norm < 7.0 -> 5.0
        else -> 10.0
    }
    return (nice * mag).toLong().coerceAtLeast(1L)
}

internal fun floorDiv(a: Long, b: Long): Long {
    var q = a / b
    if (a % b != 0L && (a < 0) != (b < 0)) q -= 1
    return q
}

internal fun ceilDiv(a: Long, b: Long): Long {
    var q = a / b
    if (a % b != 0L && (a < 0) == (b < 0)) q += 1
    return q
}

/**
 * 金额（分）→ Y 轴刻度标签：大额单位化（万/千）防溢出，小额按元经
 * [com.minibook.util.MoneyFormatter.formatFen]。仅供刻度展示，真实金额仍以 MoneyFormatter 为准。
 */
internal fun formatAxisLabel(fen: Long): String {
    if (fen == 0L) return "0"
    val sign = if (fen < 0L) "-" else ""
    val absFen = fen.absoluteValue
    val yuan = absFen / 100L
    val text = when {
        absFen >= 1_000_000L -> { // >= ¥1万
            val yi = yuan / 10_000L
            val rem = yuan % 10_000L
            if (rem == 0L) "${yi}万" else "${yi}.${(rem * 10L + 5_000L) / 10_000L}万"
        }
        absFen >= 100_000L -> { // >= ¥1千
            val qian = yuan / 1_000L
            val rem = yuan % 1_000L
            if (rem == 0L) "${qian}千" else "${qian}.${(rem * 10L + 500L) / 1_000L}千"
        }
        else -> com.minibook.util.MoneyFormatter.formatFen(absFen, withSymbol = false)
    }
    return sign + text
}

// ---------------------------------------------------------------------------
// 几何与图元
// ---------------------------------------------------------------------------

/** 可绘制区域（px 尺寸）。 */
internal class PlotArea(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

internal fun DrawScope.plotArea(): PlotArea =
    PlotArea(
        left = 46.dp.toPx(),
        right = size.width - 10.dp.toPx(),
        top = 10.dp.toPx(),
        bottom = size.height - 28.dp.toPx(),
    )

/** 值（分）→ 画布 y 坐标。 */
internal fun DrawScope.yFor(fen: Long, axis: YAxis, plot: PlotArea): Float {
    val frac = if (axis.maxFen > axis.minFen) {
        (fen - axis.minFen).toFloat() / (axis.maxFen - axis.minFen).toFloat()
    } else {
        0f
    }
    return plot.top + (1f - frac) * plot.height
}

/** 桶下标 → 画布 x 中心。 */
internal fun DrawScope.xCenterFor(index: Int, bucketCount: Int, plot: PlotArea): Float =
    if (bucketCount < 2) plot.left + plot.width / 2f
    else plot.left + (index + 0.5f) / bucketCount * plot.width

/**
 * 点击命中检测（A2）：用 [PointerInputScope.size] 复刻 [DrawScope.plotArea] 的 px 平面
 * （left=46dp/right=size.width-10dp/top=10dp/bottom=size.height-28dp），x 反解最近桶
 * `round((x-left)/width*count - 0.5)`；点出 plot 区域或桶越界 → null（清空选中）。
 */
private fun PointerInputScope.hitTestBucket(offset: Offset, bucketCount: Int): Int? {
    if (bucketCount <= 0) return null
    val left = 46.dp.toPx()
    val right = size.width.toFloat() - 10.dp.toPx()
    val top = 10.dp.toPx()
    val bottom = size.height.toFloat() - 28.dp.toPx()
    if (offset.x < left || offset.x > right || offset.y < top || offset.y > bottom) return null
    val width = (right - left).coerceAtLeast(1f)
    val index = round((offset.x - left) / width * bucketCount - 0.5f).toInt()
    return if (index in 0 until bucketCount) index else null
}

/**
 * 选中态叠加层（A2）：竖参考线（outlineVariant）+ 放大各系列选中点（3dp→5.5dp + surface 描边环）
 * + 点上方 tooltip（首行桶标签，其后每系列「label: ¥金额」）。
 */
private fun DrawScope.drawSelectionOverlay(
    index: Int,
    axis: YAxis,
    clamped: List<LineChartSeries>,
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

    // 竖参考线（outlineVariant）。
    drawLine(referenceColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())

    // 放大各系列选中点（含无 marker 的结余单系列：点击时补画选中点便于读数）。
    clamped.forEach { s ->
        val v = s.valuesFen.getOrNull(index) ?: return@forEach
        val color = s.colorForValue?.invoke(v) ?: s.color
        val center = Offset(x, yFor(v, axis, plot))
        drawCircle(radius = 5.5.dp.toPx(), center = center, color = color)
        drawCircle(
            radius = 5.5.dp.toPx(),
            center = center,
            color = tooltipBg,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }

    // tooltip 文本行：首行桶标签，其后逐系列「label: ¥金额」。
    val lines = mutableListOf(buckets.getOrNull(index).orEmpty())
    clamped.forEach { s ->
        val v = s.valuesFen.getOrNull(index) ?: return@forEach
        lines += "${s.label}: ${MoneyFormatter.formatFen(v)}"
    }
    drawTooltip(lines, Offset(x, plot.top), plot, textMeasurer, labelStyle, tooltipBg, tooltipText, tooltipBorder)
}

/** 轻量 tooltip：浅色圆角底 + 多行文本，定位在锚点上方并水平钳在 plot 内（A2）。 */
private fun DrawScope.drawTooltip(
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

internal fun DrawScope.drawYGridAndLabels(
    axis: YAxis,
    plot: PlotArea,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    zeroColor: Color,
    textColor: Color,
) {
    axis.ticks.forEach { tick ->
        if (tick == 0L) return@forEach
        val y = yFor(tick, axis, plot).coerceIn(plot.top.coerceAtMost(plot.bottom), plot.top.coerceAtLeast(plot.bottom))
        drawLine(
            color = gridColor,
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = 1f,
            alpha = 0.6f,
        )
        drawLabelRight(textMeasurer, formatAxisLabel(tick), Offset(plot.left - 6.dp.toPx(), y), style, textColor)
    }
    // 穿越零轴的结余走势：零轴用 outline 淡线强调。
    if (axis.minFen < 0L) {
        val y = yFor(0L, axis, plot)
        drawLine(zeroColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.5.dp.toPx(), alpha = 0.8f)
    }
}

internal fun DrawScope.drawXLabels(
    buckets: List<String>,
    plot: PlotArea,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    textColor: Color,
) {
    if (buckets.isEmpty()) return
    val step = if (buckets.size <= 10) 1 else ceil(buckets.size / 10.0).toInt()
    buckets.forEachIndexed { i, label ->
        if (i % step != 0 && i != buckets.lastIndex) return@forEachIndexed
        drawLabelCentered(textMeasurer, label, Offset(xCenterFor(i, buckets.size, plot), plot.bottom + 6.dp.toPx()), style, textColor)
    }
}

private fun DrawScope.drawSeries(series: LineChartSeries, axis: YAxis, plot: PlotArea, showPoints: Boolean, bucketCount: Int) {
    val values = series.valuesFen
    if (values.isEmpty() || bucketCount <= 0) return
    val points = values.mapIndexed { i, v -> Offset(xCenterFor(i, bucketCount, plot), yFor(v, axis, plot)) }
    if (points.size == 1) {
        drawCircle(series.color, radius = 4.dp.toPx(), center = points[0])
        return
    }
    val drawMarkers = showPoints && values.size <= MAX_MARKER_POINTS
    val fromColor = series.colorForValue
    if (fromColor == null) {
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, series.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (drawMarkers) points.forEach { drawCircle(series.color, radius = 3.dp.toPx(), center = it) }
    } else {
        points.forEachIndexed { i, p ->
            val c = fromColor(values[i])
            if (i < points.lastIndex) {
                drawLine(c, p, points[i + 1], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
            if (drawMarkers) drawCircle(c, radius = 3.dp.toPx(), center = p)
        }
    }
}

internal fun DrawScope.drawLabelRight(textMeasurer: TextMeasurer, text: String, anchor: Offset, style: TextStyle, color: Color) {
    val layout = textMeasurer.measure(text, style.copy(color = color))
    drawText(textMeasurer, text, Offset(anchor.x - layout.size.width, anchor.y - layout.size.height / 2f), style.copy(color = color))
}

internal fun DrawScope.drawLabelCentered(textMeasurer: TextMeasurer, text: String, anchor: Offset, style: TextStyle, color: Color) {
    val layout = textMeasurer.measure(text, style.copy(color = color))
    drawText(textMeasurer, text, Offset(anchor.x - layout.size.width / 2f, anchor.y), style.copy(color = color))
}
