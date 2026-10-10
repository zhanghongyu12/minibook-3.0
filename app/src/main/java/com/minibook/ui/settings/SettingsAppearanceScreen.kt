package com.minibook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.data.local.datastore.DarkModeSetting
import com.minibook.ui.components.ChoiceDialog
import com.minibook.ui.components.ChoiceOption
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SettingsSubPageTopBar
import com.minibook.ui.components.TrailingValue

/**
 * 设置 · 外观二级页（2026-10-10 设置页改版：设置首页只留分组汇总，逐组进二级页）。
 *
 * 内容 = 原设置页「外观」分区原样搬运：暗色模式三态 [ChoiceDialog]（软切换即时生效，F-09）
 * + 主题颜色调色盘（软切换即时生效，选「默认」恢复跟随预设）。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 设置页 VM；默认经 [hiltViewModel] 解析。
 */
@Composable
fun SettingsAppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()

    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = "外观", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            SettingSection(title = "外观", content = {
                SettingRow(
                    title = "暗色模式",
                    subtitle = "跟随系统 / 亮色 / 暗色",
                    trailing = { TrailingValue(darkMode.displayName()) },
                    onClick = { showDarkModeDialog = true },
                )
                SettingRow(
                    title = "主题颜色",
                    subtitle = "自定义全局主色，选择「默认」恢复",
                    trailing = { ThemeColorDot(themeColor) },
                    onClick = { showThemeColorDialog = true },
                )
            })
        }
    }

    // —— 暗色模式三态（F-09，软切换即时生效） ——
    if (showDarkModeDialog) {
        ChoiceDialog(
            title = "暗色模式",
            options = listOf(
                ChoiceOption(DarkModeSetting.SYSTEM.name, "跟随系统"),
                ChoiceOption(DarkModeSetting.LIGHT.name, "亮色"),
                ChoiceOption(DarkModeSetting.DARK.name, "暗色"),
            ),
            selectedKey = darkMode.name,
            onSelect = { key ->
                showDarkModeDialog = false
                DarkModeSetting.entries.firstOrNull { it.name == key }?.let(viewModel::setDarkMode)
            },
            onDismiss = { showDarkModeDialog = false },
        )
    }

    // —— 主题颜色调色盘（软切换即时生效，F-09） ——
    if (showThemeColorDialog) {
        ThemeColorPickerDialog(
            currentHex = themeColor,
            onSelect = { hex ->
                showThemeColorDialog = false
                viewModel.setThemeColor(hex)
            },
            onDismiss = { showThemeColorDialog = false },
        )
    }
}

/** 暗色模式三态 UI 文案。 */
internal fun DarkModeSetting.displayName(): String = when (this) {
    DarkModeSetting.SYSTEM -> "跟随系统"
    DarkModeSetting.LIGHT -> "亮色"
    DarkModeSetting.DARK -> "暗色"
}

/** 主题色预设项（hex 存 ARGB；label 仅用于可读性/无障碍，不参与持久化）。 */
private data class ThemeColorOption(val label: String, val hex: String)

private val themeColorPresets = listOf(
    ThemeColorOption("紫", "#6750A4"),
    ThemeColorOption("蓝", "#0061A4"),
    ThemeColorOption("青", "#006A6A"),
    ThemeColorOption("绿", "#386A20"),
    ThemeColorOption("橙", "#9A4600"),
    ThemeColorOption("粉红", "#9C3E63"),
    ThemeColorOption("靛蓝", "#3F51B5"),
    ThemeColorOption("棕", "#6D4C41"),
)

/** 主题颜色调色盘：屏幕居中 [Dialog]，「默认」清除为 null，其余 8 色即时写回。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorPickerDialog(
    currentHex: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("主题颜色", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "选择后即时生效；选「默认」恢复跟随系统预设。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeColorSwatch(
                        label = "默认",
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        selected = currentHex == null,
                        onClick = { onSelect(null) },
                    )
                    themeColorPresets.forEach { opt ->
                        parseColorSafe(opt.hex)?.let { color ->
                            ThemeColorSwatch(
                                label = opt.label,
                                color = color,
                                selected = currentHex == opt.hex,
                                onClick = { onSelect(opt.hex) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("取消") }
            }
        }
    }
}

/** 单个主题色块：40dp 圆角块 + 下方标签；选中态加粗 primary 描边。 */
@Composable
private fun ThemeColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「主题颜色」行尾部 20dp 圆形色块：当前主题色（null 时显示默认 primary）。 */
@Composable
private fun ThemeColorDot(hex: String?) {
    val color = hex?.let(::parseColorSafe) ?: MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** 解析 ARGB hex 为 Compose [Color]；非法输入回退 null。 */
private fun parseColorSafe(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
