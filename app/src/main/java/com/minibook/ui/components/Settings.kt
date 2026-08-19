package com.minibook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设置页分区（UI §4 C-23）。
 *
 * 可选分区标题 + 圆角卡片容器，行内容由 [content] 注入（[SettingRow]/[SwitchRow] 等）。
 *
 * @param title 分区标题；null 表示无标题。
 */
@Composable
fun SettingSection(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(content = content)
        }
    }
}

/**
 * 设置页行（UI §4 C-23）：标题 + 可选副标题 + 可选尾部槽 + 可选点击。
 *
 * 组件不承载业务文案；[trailing] 为尾部内容槽（如「›」箭头、值文本等）。
 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/**
 * 设置页开关行（UI §4 C-23）：标题 + [Switch]，整行可点切换。
 *
 * @param checked 开关状态（自外下行）。
 * @param enabled 是否可交互。
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
