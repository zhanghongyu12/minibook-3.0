package com.minibook.ui.reports.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.util.MoneyFormatter

/**
 * 报表·周期总计卡（TASK-036，UI §8.2 金额直观化增强）。
 *
 * 图 1 收支趋势卡之前，一行三列等宽展示「收入 / 支出 / 结余」三笔周期总额（分）。
 * 金额经 [MoneyFormatter.formatFen] 统一格式化，语义色由 [LocalSemanticColors] 下发：
 * 收入绿 / 支出红 / 结余按正负染色（>=0 收入绿、<0 支出红）。
 *
 * 纯展示组件：只接收已聚合金额（分），不参与任何算术（DEC-011）。
 */
@Composable
fun ReportSummaryCard(
    incomeFen: Long,
    expenseFen: Long,
    balanceFen: Long,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryAmount(label = "收入", fen = incomeFen, color = semantic.income, modifier = Modifier.weight(1f))
            SummaryAmount(label = "支出", fen = expenseFen, color = semantic.expense, modifier = Modifier.weight(1f))
            SummaryAmount(
                label = "结余",
                fen = balanceFen,
                color = if (balanceFen >= 0L) semantic.income else semantic.expense,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 单列：上方标签（labelSmall + onSurfaceVariant）+ 下方金额（titleLarge + 语义色）。 */
@Composable
private fun SummaryAmount(
    label: String,
    fen: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = MoneyFormatter.formatFen(fen),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
