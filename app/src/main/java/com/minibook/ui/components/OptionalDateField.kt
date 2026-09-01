package com.minibook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.util.TimeFormatter
import java.time.LocalDate

/**
 * 可选日期字段（C-10 口径扩展，支持「未设置」+ 可清空）：Null 表示不过滤。
 * 复用 [Surface] 样式 + [DatePickerDialog]；展示 `yyyy-MM-dd`（经 [TimeFormatter.formatDate]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionalDateField(
    label: String,
    date: String?,
    onDateChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                date?.let { TimeFormatter.formatDate(it) } ?: "未设置",
                style = MaterialTheme.typography.bodyLarge,
                color = if (date != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (date != null) {
                IconButton(
                    onClick = { onDateChange(null) },
                    modifier = Modifier.size(24.dp).semantics { contentDescription = "清除$label" },
                ) {
                    Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateToUtcMillis(date ?: LocalDate.now().toString()),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    utcMillisToDate(pickerState.selectedDateMillis)?.let(onDateChange)
                    showDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}