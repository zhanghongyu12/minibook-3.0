package com.minibook.ui.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.SheetFormScaffold
import com.minibook.util.Validators

/**
 * 预算编辑弹层（TASK-038，UI §8.3 P-14 / DEC-008）。
 *
 * 两种模式（[BudgetEditorMode]）：
 * - **[TOTAL]**：编辑/设置月度总预算。金额经 [MoneyInputField]（元↔分），保存 →
 *   [onConfirmTotal]（[com.minibook.ui.budgets.BudgetsViewModel.setTotalBudget]，
 *   `SetTotalBudgetUseCase`，总额「先删后插」在 Repository 事务内闭合，DEC-008）；
 * - **[CATEGORY]**：设置分类预算。分类选择（[CategoryPickerSheet] C-02 限定 `type=expense`）
 *   + 金额，保存 → [onConfirmCategory]（`SetCategoryBudgetUseCase`，唯一由 DB
 *   `UNIQUE(ledgerId, month, categoryId)` 保证，DEC-008）；编辑已有分类（[initialCategory] 非空）
 *   时展示「删除该分类预算」→ [onDelete]（`ClearCategoryBudgetUseCase`）。
 *
 * 复用 [SheetFormScaffold]（C-17）/ [MoneyInputField]（C-01）/ [CategoryPickerSheet]（C-02）/
 * [ConfirmDialog]（C-18，danger 删除）；颜色一律走暗色 token（[MaterialTheme.colorScheme]）。
 *
 * @param mode 弹层模式（总预算 / 分类预算）。
 * @param initialCategory 分类预算编辑时的默认分类；null 表示新增（需先选分类）。
 * @param initialAmountFen 预填金额（分）；编辑回填、新增为 null。
 * @param categories 支出分类列表（[CategoryPickerSheet] 候选，`type=expense`）。
 * @param onConfirmTotal 保存月度总预算回调（金额分）；仅 TOTAL 模式使用。
 * @param onConfirmCategory 保存分类预算回调（分类 + 金额分）；仅 CATEGORY 模式使用。
 * @param onDelete 删除回调（存在编辑目标时非 null，展示 danger 删除入口）。
 * @param onDismiss 关闭回调（保存成功/取消均回调）。
 * @param serverError 保存失败内联文案；null 不展示。
 * @param saving 保存进行中：置灰确认按钮。
 */
@Composable
fun BudgetEditorSheet(
    mode: BudgetEditorMode,
    initialCategory: Category?,
    initialAmountFen: Long?,
    categories: List<Category>,
    onConfirmTotal: (Long) -> Unit,
    onConfirmCategory: (Category, Long) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    serverError: String? = null,
    saving: Boolean = false,
) {
    var amountFen by remember { mutableStateOf(initialAmountFen) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isCategoryMode = mode == BudgetEditorMode.CATEGORY
    // 金额校验（>0 且不超上限）在弹层内先拦截，UseCase 再校验一遍（双保险）。
    val amountValid = amountFen?.let { Validators.validateAmount(it) } ?: false
    val confirmEnabled = amountValid && (!isCategoryMode || selectedCategory != null) && !saving

    val title = when (mode) {
        BudgetEditorMode.TOTAL ->
            if (initialAmountFen != null) "编辑总预算" else "设置总预算"
        BudgetEditorMode.CATEGORY ->
            if (initialCategory != null) "编辑分类预算" else "设置分类预算"
    }

    SheetFormScaffold(
        title = title,
        onConfirm = {
            val amount = amountFen
            if (amount != null) {
                when (mode) {
                    BudgetEditorMode.TOTAL -> onConfirmTotal(amount)
                    BudgetEditorMode.CATEGORY -> selectedCategory?.let { onConfirmCategory(it, amount) }
                }
            }
        },
        onDismiss = onDismiss,
        confirmEnabled = confirmEnabled,
        confirmLabel = "保存",
        modifier = modifier,
    ) {
        if (isCategoryMode) {
            CategorySelectField(
                category = selectedCategory,
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        MoneyInputField(
            fen = amountFen,
            onFenChange = { amountFen = it },
            label = "预算金额",
        )

        // 金额非法时内联错误（优先于服务端错误展示）。
        if (amountFen != null && !amountValid) {
            Spacer(Modifier.height(6.dp))
            Text(
                "请输入大于 0 的预算金额",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // 服务端保存失败内联展示（保留弹层）。
        if (serverError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                serverError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // 编辑已有分类预算的可删除入口（danger 删除，C-18 强确认后走 onDelete）。
        if (isCategoryMode && selectedCategory != null && onDelete != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Text("删除该分类预算", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // 分类选择弹层（C-02，限定 type=expense）：作为 SheetFormScaffold 的兄弟弹层叠放。
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            type = CategoryType.EXPENSE,
            excludeId = null,
            initialId = selectedCategory?.id,
            title = "选择预算分类",
            onPick = { category ->
                selectedCategory = category
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // 删除分类预算强确认（C-18，danger）。
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除分类预算",
            message = "确定删除「${selectedCategory?.name ?: ""}」当月的分类预算吗？",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * 分类选择入口字段：[Surface] 行，展示已选分类（图标 + 名称），未选时提示「请选择分类」。
 *
 * @param category 已选分类；null 表示未选（点击弹分类选择器）。
 * @param onClick 点击回调（弹 [CategoryPickerSheet]）。
 */
@Composable
private fun CategorySelectField(
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (category != null) {
                Text(category.icon, fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    "请选择分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 预算编辑弹层模式（UI §8.3 P-14）。 */
enum class BudgetEditorMode {
    /** 月度总预算（categoryId=null，先删后插唯一，DEC-008）。 */
    TOTAL,

    /** 分类预算（categoryId 必填，DB UNIQUE 保证唯一，DEC-008）。 */
    CATEGORY,
}
