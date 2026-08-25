package com.minibook.ui.reports.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 图例（报表，UI §8.2 C-30）。
 *
 * 色块 + 名称（+ 可选占比 %）。环形旁展示分类占比，折线/柱状下方展示系列色块。
 * 色值一律由调用方传入语义色/分类色（走 token，不在此硬编码）。
 *
 * @param onItemClick 非空时整行可点击并回调其下标（如分类占比图例 → 跳转筛明细）；null 不可点击。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((Int) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            LegendItemRow(item = item, onClick = onItemClick?.let { cb -> { cb(index) } })
        }
    }
}

/** 图例项：色块 + 名称 + 可选占比。 */
data class LegendItem(
    val label: String,
    val color: Color,
    val percentText: String? = null,
)

@Composable
private fun LegendItemRow(item: LegendItem, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Spacer(
            Modifier
                .width(12.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(item.color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.percentText?.let { pct ->
            Spacer(Modifier.width(4.dp))
            Text(
                text = pct,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
