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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

/**
 * 设置行尾部「当前值」文本（2026-10-10 由 SettingsScreen 提取为公共组件，供设置二级页复用）。
 *
 * @param text 当前值文案（如「跟随系统」「1 分钟后」）。
 */
@Composable
fun TrailingValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 设置行尾部「›」箭头（表达「可进入下级/可点开」）：2026-10-12 换自带矢量图标（TASK-079）。 */
@Composable
fun TrailingChevron() {
    Icon(
        imageVector = MiniBookIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 设置二级页顶栏（2026-10-10 设置页改版）：标题 + 返回。
 *
 * 2026-10-12（TASK-079）：返回图标改用自带矢量 [MiniBookIcons.ArrowBack]（此前是 `Text("←")`
 * 占位，与导入页/账单导入页同口径 TASK-020 偏差；emoji/文字图标无法随主题着色，已逐步替换）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubPageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiniBookIcons.ArrowBack, contentDescription = "返回")
            }
        },
    )
}
