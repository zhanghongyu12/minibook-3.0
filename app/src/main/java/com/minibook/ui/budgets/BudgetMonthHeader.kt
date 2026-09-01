package com.minibook.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.util.TimeFormatter
import java.time.YearMonth

/**
 * 预算页顶栏：「预算」+ 返回 ← + 月份切换（[MonthSelector]）。
 *
 * @param month 当前查看月份。
 * @param onMonthChange 月份切换回调。
 * @param onBack 返回回调（NavHost 接线传入 `popBackStack()`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetsTopBar(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("预算", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(16.dp))
                MonthSelector(month = month, onMonthChange = onMonthChange)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Text("←") }
        },
    )
}

/** 月份选择器（UI §8.3「2026-08 ▾」）：点击弹月份选择对话框。 */
@Composable
private fun MonthSelector(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { showPicker = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(TimeFormatter.formatYearMonth(month), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showPicker) {
        MonthPickerDialog(
            initial = month,
            onSelect = {
                onMonthChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 月份选择对话框：◀ 上一月 / ▶ 下一月 步进，确定回传所选月份。 */
@Composable
private fun MonthPickerDialog(
    initial: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayed by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { displayed = displayed.minusMonths(1) }) { Text("◀") }
                    Spacer(Modifier.width(12.dp))
                    Text(TimeFormatter.formatYearMonth(displayed), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { displayed = displayed.plusMonths(1) }) { Text("▶") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(displayed) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}