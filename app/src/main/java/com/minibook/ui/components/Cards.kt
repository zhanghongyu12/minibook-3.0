package com.minibook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 净额卡（UI §4 C-12）：大字净额金额（tnum，经 [MoneyText]）+ 明细行。
 *
 * 适配收支结余与债务净额：标题/净额/语义色由调用方注入，明细行 [rows] 可空。
 *
 * @param balanceFen 净额（分），可为负；负值由 [MoneyText] 展示负号。
 * @param semantic 净额语义色（INCOME/EXPENSE/NET_ZERO 等）。
 */
@Composable
fun NetBalanceCard(
    title: String,
    balanceFen: Long,
    semantic: MoneySemantic,
    modifier: Modifier = Modifier,
    rows: List<NetBalanceRow> = emptyList(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            MoneyText(
                fen = balanceFen,
                semantic = semantic,
                style = MaterialTheme.typography.displaySmall,
            )
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(
                            fen = row.fen,
                            semantic = row.semantic,
                            style = MaterialTheme.typography.bodyMedium,
                            withSymbol = false,
                        )
                    }
                }
            }
        }
    }
}

/** [NetBalanceCard] 明细行数据：标签 + 金额（分）+ 语义色。 */
data class NetBalanceRow(
    val label: String,
    val fen: Long,
    val semantic: MoneySemantic,
)
