package com.minibook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 表单 Sheet 通用骨架（UI §4 C-17）。
 *
 * 基于 [ModalBottomSheet]：标题 + 内容槽 + 保存/取消按钮 + 内容区滚动与最大高度限制。
 * 内容由调用方经 [content] 注入（`ColumnScope`），不承载业务文案；
 * 「保存/取消」为通用 UI 文案，确认按钮文案可用 [confirmLabel] 覆盖。
 *
 * @param title 表单标题。
 * @param onConfirm 保存回调（[confirmEnabled] 控制可用态）。
 * @param onDismiss 取消/关闭回调。
 * @param confirmEnabled 保存按钮可用态。
 * @param confirmLabel 确认按钮文案，默认「保存」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetFormScaffold(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    confirmLabel: String = "保存",
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                content = content,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text(confirmLabel) }
            }
        }
    }
}
