package com.minibook.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 只读明细行：左侧标签 + 右侧值槽。
 *
 * 交易明细已迁至全屏详情页 [com.minibook.ui.transactions.TransactionDetailScreen]（不再弹层），
 * 本行为债务只读明细 Sheet（[DebtDetailSheet]）共用。`label` 列定宽、`value` 弹性。
 * 参数顺序：`value` 置末以支持 trailing lambda（`DetailRow("标签") { ... }`）。
 */
@Composable
internal fun DetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Box(Modifier.weight(1f)) { value() }
    }
}