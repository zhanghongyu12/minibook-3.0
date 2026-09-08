package com.minibook.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.minibook.util.MoneyFormatter
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 环形占比图（报表·分类占比，UI §8.2 C-29）。
 *
 * 原生 [Canvas] 自绘，`drawArc`。sweep 按占比映射 360°，收入 / 支出一次一张（调用方切换口径）。
 * 只接收已聚合数据（占比值域已由 DB 端/domain 汇总），**不承担任何算术**（DEC-011）。
 *
 * 降级：0 有效数据只画浅色空环不崩（空态文案由 [ChartEmptyState] 呈现）；1 个分类画满圈。
 *
 * @param slices 环形切片（分类名 + 分类色 + 金额），由调用方映射而来；
 *   分类自有 color 为唯一允许的硬编码色来源。
 * @param modifier 尺寸控制（建议正方形，绘制时按内容居中并取最小边）。
 * @param onSliceClick 点击命中某扇区时回调其下标（保留既有选中高亮）；null 表示仅选中不高亮跳转。
 * @param onCenterClick 非空且已有选中切片（[selectedIndex] 非空）时，点击环心文本区（中心数据框）
 *   回调，用于「点击中间数据跳转明细」；未选中切片时点击中心忽略。
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    onSliceClick: ((Int) -> Unit)? = null,
    onCenterClick: (() -> Unit)? = null,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val centerTextColor = MaterialTheme.colorScheme.onSurface
    val centerTextStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    // 点击选中的切片下标（A2「点击看数值」）；null = 未选中，点出环带/角度未落切片即清空。
    var selectedIndex by remember(slices) { mutableStateOf<Int?>(null) }
    // 当前选中切片中心文本（三行）的命中矩形：draw 时写入、pointer 命中检测读取（非快照状态）。
    val centerBounds = remember { arrayOfNulls<Rect>(1) }

    Canvas(
        modifier.pointerInput(slices) {
            detectTapGestures { offset ->
                // 两步交互：第一下点扇区仅选中并显示中心数据框；第二下点中心数据框才跳转明细。
                if (selectedIndex != null && centerBounds[0]?.contains(offset) == true) {
                    onCenterClick?.invoke()
                    return@detectTapGestures
                }
                val hit = hitTestSlice(offset, slices)
                selectedIndex = hit
                if (hit != null) onSliceClick?.invoke(hit)
                else centerBounds[0] = null
            }
        },
    ) {
        centerBounds[0] = drawDonut(slices, trackColor, selectedIndex, textMeasurer, centerTextStyle, centerTextColor)
    }
}

/** 环形切片：名称 + 分类色 + 金额（分）。 */
data class DonutSlice(
    val label: String,
    val color: Color,
    val valueFen: Long,
)

/** 生成环形图例项（名称 + 色块 + 金额 + 占比 %），用于 [ChartLegend]。 */
fun buildDonutLegendItems(slices: List<DonutSlice>): List<LegendItem> {
    val total = slices.sumOf { it.valueFen }
    return slices
        .filter { it.valueFen > 0L }
        .map {
            LegendItem(
                label = it.label,
                color = it.color,
                percentText = percentText(it.valueFen, total),
                amountText = MoneyFormatter.formatFen(it.valueFen),
            )
        }
}

/** 整数占比文本（xx.x%），不经浮点，金额语义安全。 */
internal fun percentText(partFen: Long, totalFen: Long): String {
    if (totalFen <= 0L) return "0.0%"
    val perMille = partFen * 1_000L / totalFen
    return "${perMille / 10}.${perMille % 10}%"
}

/**
 * 环形命中检测（A2）：点相对圆心距离落在环带内（stroke 28dp 的内外半径间）且
 * `atan2` 角度落到某切片 sweep 区间 → 选中该切片下标；否则 null。
 */
private fun PointerInputScope.hitTestSlice(offset: Offset, slices: List<DonutSlice>): Int? {
    val total = slices.sumOf { it.valueFen }
    if (total <= 0L) return null

    val strokeWidth = 28.dp.toPx()
    val diameter = min(size.width.toFloat(), size.height.toFloat())
    val radius = (diameter / 2f - strokeWidth / 2f).coerceAtLeast(1f)
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    val dx = offset.x - centerX
    val dy = offset.y - centerY
    val dist = sqrt(dx * dx + dy * dy)
    val inner = (radius - strokeWidth / 2f).coerceAtLeast(0f)
    val outer = radius + strokeWidth / 2f
    if (dist < inner || dist > outer) return null

    // 角度：0° = 右（+x），顺时针增长；drawDonut 从 -90°（12 点方向）起 sweep。
    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val fromTop = ((deg + 90f) % 360f + 360f) % 360f

    var start = 0f
    slices.forEachIndexed { i, s ->
        if (s.valueFen <= 0L) return@forEachIndexed
        val sweep = (s.valueFen.toDouble() / total.toDouble() * 360f).toFloat()
        if (fromTop >= start && fromTop < start + sweep) return i
        start += sweep
    }
    return null
}

/**
 * 中心数据框布局纯函数：由已测量的文本尺寸（maxWidth/lineHeight/行数）+ 环心坐标计算三行
 * 居中文本的整体命中矩形（宽=最宽行、高=lineHeight×行数，相对环心居中）。供
 * [drawDonutCenterText] 定位与 pointer 命中检测共用，使「点中间数据跳转」仅命中实际文字区、
 * 不误触整片内孔。
 */
internal fun Density.donutCenterTextRect(
    centerX: Float,
    centerY: Float,
    maxWidth: Int,
    lineHeight: Int,
    lineCount: Int,
): Rect {
    val totalHeight = lineHeight * lineCount
    val left = centerX - maxWidth / 2f
    val top = centerY - totalHeight / 2f
    return Rect(left, top, left + maxWidth, top + totalHeight)
}

private fun DrawScope.drawDonut(
    slices: List<DonutSlice>,
    trackColor: Color,
    selectedIndex: Int?,
    textMeasurer: TextMeasurer,
    centerTextStyle: TextStyle,
    centerTextColor: Color,
): Rect? {
    val strokeWidth = 28.dp.toPx()
    val selectedStrokeWidth = 34.dp.toPx()
    val diameter = min(size.width, size.height)
    val radius = (diameter / 2f - strokeWidth / 2f).coerceAtLeast(1f)
    val topLeft = Offset(
        (size.width - diameter) / 2f + strokeWidth / 2f,
        (size.height - diameter) / 2f + strokeWidth / 2f,
    )
    val arcSize = Size(radius * 2f, radius * 2f)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

    // 底环：贴合卡片 surface 的分隔色，保证与底分离（暗色叠加描边）。
    drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )

    val total = slices.sumOf { it.valueFen }
    if (total <= 0L) return null

    var start = -90f
    slices.forEachIndexed { i, slice ->
        if (slice.valueFen <= 0L) return@forEachIndexed
        val sweep = (slice.valueFen.toDouble() / total.toDouble() * 360f).toFloat()
        // 选中切片加粗 28dp → 34dp，其余仍 28dp（金额语义色不动）。
        val width = if (i == selectedIndex) selectedStrokeWidth else strokeWidth
        drawArc(
            color = slice.color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Butt),
        )
        start += sweep
    }

    // 选中切片：环心画 分类名 + 金额 + 占比（居中 drawText），返回文本命中矩形供 pointer 检测。
    val index = selectedIndex ?: return null
    val slice = slices.getOrNull(index) ?: return null
    return drawDonutCenterText(slice, total, textMeasurer, centerTextStyle, centerTextColor)
}

/** 环心三行文本（分类名 / 金额 / 占比），垂直居中、水平逐行居中（A2）。返回文本整体矩形。 */
private fun DrawScope.drawDonutCenterText(
    slice: DonutSlice,
    total: Long,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    color: Color,
): Rect? {
    val textStyle = style.copy(color = color)
    val lines = listOf(
        slice.label,
        MoneyFormatter.formatFen(slice.valueFen),
        percentText(slice.valueFen, total),
    )
    val layouts = lines.map { textMeasurer.measure(it, textStyle) }
    val lineHeight = layouts.maxOfOrNull { it.size.height } ?: return null
    val maxWidth = layouts.maxOfOrNull { it.size.width } ?: return null
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val rect = donutCenterTextRect(centerX, centerY, maxWidth, lineHeight, lines.size)
    var cursorY = rect.top
    layouts.forEachIndexed { i, layout ->
        drawText(textMeasurer, lines[i], Offset(centerX - layout.size.width / 2f, cursorY), textStyle)
        cursorY += lineHeight
    }
    return rect
}
