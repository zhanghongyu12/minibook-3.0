package com.minibook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.Ledger
import com.minibook.util.TimeFormatter
import java.time.DayOfWeek
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 日期选择字段（UI §4 C-10）。
 *
 * 展示 `yyyy-MM-dd`（经 [TimeFormatter.formatDate]），点击弹出 [DatePickerDialog]；
 * 默认今天（`date` 非法时回退今天）。无业务文案，仅含「确定/取消」通用词与日历 emoji。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    date: String,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val initialMillis = remember(date) { dateToUtcMillis(date) }

    Surface(
        onClick = { showDialog = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeFormatter.formatDate(date),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("📅", fontSize = 20.sp)
        }
    }

    if (showDialog) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
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

/**
 * 列表按日分组头（UI §4 C-11）：`2026-08-18 · 周六`。
 *
 * 日期经 [TimeFormatter.formatDate] 格式化；星期文案（周一~周日）为通用日历语义词，
 * 属组件内纯展示映射。
 */
@Composable
fun DateSectionHeader(
    date: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildString {
            append(TimeFormatter.formatDate(date))
            val weekday = weekdayLabel(date)
            if (weekday.isNotBlank()) {
                append(" · ")
                append(weekday)
            }
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 顶部账本选择器（UI §4 C-16）：当前账本名 + ▾。
 *
 * 无账本（boot 前）显示占位「账本」。文案「账本」为通用语义词。
 */
@Composable
fun LedgerSelector(
    ledger: Ledger?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ledger != null) {
            Text(ledger.icon, fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                ledger.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("账本", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(4.dp))
        Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** `yyyy-MM-dd` → UTC 当日零点毫秒（DatePicker 用）。 */
private fun dateToUtcMillis(date: String): Long {
    val localDate = try {
        LocalDate.parse(date)
    } catch (e: DateTimeException) {
        LocalDate.now()
    }
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** DatePicker 选中毫秒（UTC 当日零点）→ `yyyy-MM-dd`；null 表示未选中。 */
private fun utcMillisToDate(millis: Long?): String? {
    if (millis == null) return null
    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return TimeFormatter.formatDate(localDate.toString())
}

/** `yyyy-MM-dd` → 星期中文（周一~周日）；解析失败返回空串。 */
private fun weekdayLabel(date: String): String {
    val day = try {
        LocalDate.parse(date).dayOfWeek
    } catch (e: DateTimeException) {
        return ""
    }
    return when (day) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
