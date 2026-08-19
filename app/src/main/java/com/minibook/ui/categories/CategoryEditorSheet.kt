package com.minibook.ui.categories

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.BuiltInCategories
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.ui.components.SheetFormScaffold

/**
 * 分类编辑弹层（TASK-026，UI §2 P-05 / C-17 表单骨架）。
 *
 * 编辑态（[category] 非空）回填 name/icon/color，`type` 只读展示（锁定，UseCase 亦拒绝改）；
 * 新建态（[category] 为 null）`type` 用 [SegmentedButton] 选择（必填，默认 [type] 或支出）。
 *
 * 规则：
 * - **「其他」name 只读**（DEC-007，`order==OTHERS_ORDER(99)` 或 `name=="其他"`），
 *   icon/color 仍可改；其余内置/自定义 name 可改；
 * - **删除入口**：仅自定义分类（[onDelete] 非空）在表单底部展示「删除分类」按钮——
 *   UI 层保证内置分类不传 [onDelete]（禁删 DEC-007）；
 * - [serverError] 内联展示 Create/Update 失败信息；[saving] 时确认按钮置灰防重复提交。
 *
 * @param category 编辑目标；null 表示新建态。
 * @param type 新建态默认类型；null 时默认支出。编辑态忽略（用 [Category.type]）。
 * @param onConfirm 确认回调（name/icon/color/type）；创建/保存由调用方路由到 VM。
 * @param onDelete 删除回调；null 不展示删除按钮（内置分类禁删）。
 * @param serverError Create/Update 失败文案；null 不展示。
 * @param saving 保存进行中：置灰确认按钮。
 */
@Composable
fun CategoryEditorSheet(
    category: Category?,
    type: CategoryType?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: String, type: CategoryType) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    serverError: String? = null,
    saving: Boolean = false,
) {
    val isEdit = category != null
    val isOthers = category != null &&
        (category.order == BuiltInCategories.OTHERS_ORDER || category.name == "其他")

    var name by remember(category, type) { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember(category, type) { mutableStateOf(category?.icon.orEmpty()) }
    var color by remember(category, type) { mutableStateOf(category?.color.orEmpty()) }
    var selectedType by remember(category, type) {
        mutableStateOf(category?.type ?: type ?: CategoryType.EXPENSE)
    }

    val confirmEnabled = name.isNotBlank() && !saving

    SheetFormScaffold(
        title = if (isEdit) "编辑分类" else "新建分类",
        onConfirm = { onConfirm(name.trim(), icon.trim(), color.trim(), selectedType) },
        onDismiss = onDismiss,
        confirmEnabled = confirmEnabled,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            enabled = !isOthers,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isOthers) {
            Spacer(Modifier.height(4.dp))
            Text(
                "「其他」为兜底分类，名称不可修改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = icon,
            onValueChange = { icon = it.take(4) },
            label = { Text("图标（Emoji）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
        Spacer(Modifier.height(16.dp))
        if (isEdit) {
            Text("类型", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (selectedType == CategoryType.EXPENSE) "支出" else "收入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedType == CategoryType.EXPENSE,
                    onClick = { selectedType = CategoryType.EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("支出") },
                )
                SegmentedButton(
                    selected = selectedType == CategoryType.INCOME,
                    onClick = { selectedType = CategoryType.INCOME },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("收入") },
                )
            }
        }
        if (serverError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                serverError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (isEdit && onDelete != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除分类")
            }
        }
    }
}

/**
 * 预设分类色快捷选择行：可横向滚动的色圆，点击即选；当前选中项以主题 primary 描边高亮。
 * 预设色取自内置分类种子调色板（DATABASE §8），DB 分类色为唯一硬编码色来源（C-03）。
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
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
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
