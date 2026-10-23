package com.minibook.ui.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.minibook.domain.model.Ledger
import com.minibook.ui.components.LedgerGlyph
import com.minibook.ui.components.LedgerIcons
import com.minibook.ui.components.SheetFormScaffold

/**
 * 账本编辑弹层（TASK-029，UI §2 P-11 / C-17 表单骨架）。
 *
 * 编辑态（[ledger] 非空）回填 name/icon/color；新建态（[ledger] 为 null）为空表单，图标预选
 * [LedgerIcons.BOOK]。三个字段 **均必填**（名称 / 图标 key / 颜色 `#RRGGBB`），任一为空时确认按钮置灰。
 *
 * 图标字段为 **矢量图标网格**（[LedgerIconPickerGrid]，6 列 16 个，2026-10-13 起取代 emoji 行 +
 * 自定义 emoji 输入框）；与颜色字段「预设色行 + 手输兜底」同交互口径。
 * `Ledger.icon` 仍是 String：新选值为 [LedgerIcons] 的稳定 key，老数据里的 emoji 由
 * [LedgerGlyph] 的 `LEGACY_EMOJI` 兜底渲染，不点选就不改写。
 *
 * 确认回调 [onConfirm] 只回传三个可编辑字段，由调用方路由到 VM create/update
 * （新建走 [CreateLedgerUseCase]，编辑走 [LedgerRepository.update]，TASK-029 决定）。
 *
 * @param ledger 编辑目标；null 表示新建态。
 * @param onDismiss 取消/关闭回调。
 * @param onConfirm 确认回调（name/icon/color）。
 */
@Composable
fun LedgerEditorSheet(
    ledger: Ledger? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEdit = ledger != null
    var name by remember(ledger) { mutableStateOf(ledger?.name.orEmpty()) }
    var icon by remember(ledger) {
        mutableStateOf(ledger?.icon?.takeIf { it.isNotBlank() } ?: LedgerIcons.BOOK)
    }
    var color by remember(ledger) { mutableStateOf(ledger?.color.orEmpty()) }

    // 三字段均必填（TASK-029 规格），任一为空禁用确认。
    val confirmEnabled = name.isNotBlank() && icon.isNotBlank() && color.isNotBlank()

    SheetFormScaffold(
        title = if (isEdit) "编辑账本" else "新建账本",
        onConfirm = { onConfirm(name.trim(), icon.trim(), color.trim()) },
        onDismiss = onDismiss,
        confirmEnabled = confirmEnabled,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("图标", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        LedgerIconPickerGrid(selected = icon, onSelect = { icon = it })
        Spacer(Modifier.height(16.dp))
        Text("颜色", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        ColorPresetRow(selected = color, onSelect = { color = it })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = color,
            onValueChange = { color = it },
            label = { Text("颜色（#RRGGBB）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 账本图标选择网格（2026-10-13，用户原话：「账本的图标也换一下」）。
 *
 * 取代原来的 emoji 快捷行 + 「自定义图标（Emoji）」输入框：6 列圆钮铺满
 * [LedgerIcons.pickerKeys] 的 16 个矢量图标，点选即定；当前选中项以主题 primary 描边 +
 * `primaryContainer` 底色高亮（与 [ColorPresetRow] 同交互口径）。
 *
 * 老账本的 emoji 仍在 DB 里原样保留、由 [LedgerGlyph] 兜底渲染；只要用户不重新点选，
 * 就不会被改写（与分类编辑器同一口径）。
 *
 * @param selected 当前选中 key（空串表示未选）。
 * @param onSelect 选中回调。
 */
@Composable
private fun LedgerIconPickerGrid(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LedgerIcons.pickerKeys.chunked(columns).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                rowKeys.forEach { key ->
                    LedgerIconChoice(
                        key = key,
                        selected = selected == key,
                        onClick = { onSelect(key) },
                    )
                }
                // 末行不足一行时用等宽占位撑住左对齐（与分类图标网格同做法）。
                repeat(columns - rowKeys.size) { Spacer(Modifier.size(44.dp)) }
            }
        }
    }
}

/** 网格里的单个账本图标钮。 */
@Composable
private fun LedgerIconChoice(
    key: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LedgerGlyph(
            icon = key,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            size = 24.dp,
        )
    }
}

/**
 * 预设账本色快捷选择行：可横向滚动的色圆，点击即选；当前选中项以主题 primary 描边高亮。
 *
 * 与分类编辑器（C-03）同口径：预设色为硬编码 `#RRGGBB`，选中后落库为账本 color；
 * DB 账本色是唯一硬编码色来源例外（TASK-029 约束「账本 color 读自 DB 例外」），
 * 页面内其余颜色一律走主题 token。
 *
 * @param selected 当前选中色（`#RRGGBB`）。
 * @param onSelect 选中回调。
 */
@Composable
private fun ColorPresetRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = listOf(
        "#5D6D7E", "#4ECDC4", "#45B7D1", "#96CEB4",
        "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F",
        "#BB8FCE", "#F0B27A", "#85C1E9", "#2ECC71",
        "#F39C12", "#3498DB", "#9B59B6", "#E74C3C",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { hex ->
            val circleColor = parseHexColor(hex, MaterialTheme.colorScheme.outline)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(circleColor)
                    .then(
                        if (selected.equals(hex, ignoreCase = true)) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        },
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}

/** 解析 `#RRGGBB` → Compose Color；空/非法格式回退 fallback（同 C-03 口径）。 */
private fun parseHexColor(colorHex: String, fallback: Color): Color =
    if (colorHex.isBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallback)
    }
