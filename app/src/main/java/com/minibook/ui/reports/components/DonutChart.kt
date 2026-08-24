package com.minibook.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.CategoryAgg
import kotlin.math.min

/**
 * 环形占比图（报表·分类占比，UI §8.2 C-29）。
 *
 * 原生 [Canvas] 自绘，`drawArc`。sweep 按占比映射 360°，收入 / 支出一次一张（调用方切换口径）。
 * 只接收已聚合数据（占比值域已由 DB 端/domain 汇总），**不承担任何算术**（DEC-011）。
 *
 * 降级：0 有效数据只画浅色空环不崩（空态文案由 [ChartEmptyState] 呈现）；1 个分类画满圈。
 *
 * @param slices 环形切片（分类名 + 分类色 + 金额），由调用方从 [List<CategoryAgg>] 映射而来；
 *   分类自有 color 为唯一允许的硬编码色来源。
 * @param modifier 尺寸控制（建议正方形，绘制时按内容居中并取最小边）。
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        drawDonut(slices, trackColor)
    }
}

/**
 * 便捷重载：直接从 [List<CategoryAgg>] 渲染，[visualFor] 完成「分类 → 切片（名称/色/金额）」映射。
 *
 * 分类名/图标/色由 UI 经既有分类流做 id→元信息映射（DATABASE §10.3），由调用方注入。
 */
@Composable
fun DonutChart(
    aggregates: List<CategoryAgg>,
    modifier: Modifier = Modifier,
    visualFor: (CategoryAgg) -> DonutSlice,
) {
    DonutChart(aggregates.map { visualFor(it) }, modifier)
}

/** 环形切片：名称 + 分类色 + 金额（分）。 */
data class DonutSlice(
    val label: String,
    val color: Color,
    val valueFen: Long,
)

/** 生成环形图例项（名称 + 色块 + 占比 %），用于 [ChartLegend]。 */
fun buildDonutLegendItems(slices: List<DonutSlice>): List<LegendItem> {
    val total = slices.sumOf { it.valueFen }
    return slices
        .filter { it.valueFen > 0L }
        .map { LegendItem(it.label, it.color, percentText(it.valueFen, total)) }
}

/** 整数占比文本（xx.x%），不经浮点，金额语义安全。 */
internal fun percentText(partFen: Long, totalFen: Long): String {
    if (totalFen <= 0L) return "0.0%"
    val perMille = partFen * 1_000L / totalFen
    return "${perMille / 10}.${perMille % 10}%"
}

private fun DrawScope.drawDonut(slices: List<DonutSlice>, trackColor: Color) {
    val strokeWidth = 28.dp.toPx()
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
    if (total <= 0L) return

    var start = -90f
    slices.filter { it.valueFen > 0L }.forEach { slice ->
        val sweep = (slice.valueFen.toDouble() / total.toDouble() * 360f).toFloat()
        drawArc(
            color = slice.color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        start += sweep
    }
}
