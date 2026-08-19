package com.minibook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 确认对话框（UI §4 C-18）。
 *
 * 普通 / danger 变体：danger 时确认按钮以 `errorContainer` 强调。
 * 文案由调用方注入；「取消」为通用 UI 文案。
 *
 * @param confirmLabel 确认按钮文案。
 * @param danger 是否危险操作变体（确认按钮 errorContainer 强调）。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (danger) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(confirmLabel) }
            } else {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        modifier = modifier,
    )
}

/**
 * 删除被引用分类的迁移确认对话框（UI §4 C-19）。
 *
 * 展示引用计数与迁移目标；目标名可空（调用方尚未选定）时提示选择迁移目标。
 * 文案为分类删除域固定文案。
 *
 * @param refCount 被引用记录数。
 * @param targetName 迁移目标分类名；null 表示尚未选择。
 */
@Composable
fun MigrationDialog(
    refCount: Int,
    targetName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除分类") },
        text = {
            Text(
                if (targetName != null) {
                    "该分类已被 $refCount 笔记录引用，删除后数据将迁移至「$targetName」。"
                } else {
                    "该分类已被 $refCount 笔记录引用，请先选择要迁移到的分类。"
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认迁移") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        modifier = modifier,
    )
}

/** 单选对话框选项数据。 */
data class ChoiceOption(
    val key: String,
    val label: String,
)

/**
 * 单选对话框（UI §4 C-22）。
 *
 * 选项列表由调用方注入；点选即 [onSelect] 回调所选 key（外层负责关闭）。
 * 「取消」为通用 UI 文案。
 */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<ChoiceOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    RadioRow(
                        label = option.label,
                        selected = option.key == selectedKey,
                        onClick = { onSelect(option.key) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        modifier = modifier,
    )
}

/**
 * 单选行（UI §4 C-22 附属）：RadioButton + 文案，整行可点。
 */
@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
