package com.minibook.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Transaction
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.DatePickerField
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.SheetFormScaffold
import com.minibook.ui.components.TransactionTypeTab
import com.minibook.util.Validators

/**
 * 记一笔 / 编辑记一笔 Sheet（TASK-028，UI §2 P-07）。
 *
 * 构成：
 * - [SheetFormScaffold] 承载，标题「记一笔」（编辑态「编辑记一笔」）；
 * - [TransactionTypeTab] 收支类型切换（C-08，选中切 type → 分类列表经 VM flatMapLatest 重订阅）；
 * - [MoneyInputField] 金额（分，2 位小数、0/负/超限内部拦截 + [TransactionSheetViewModel.save] 双保险）；
 * - 分类选择（Surface 行 → [CategoryPickerSheet] 四列网格，按 type 过滤）；
 * - [DatePickerField] 日期 + 备注（≤200 字实时计数）；
 * - 编辑态底部「删除」→ danger [ConfirmDialog] 强确认。
 *
 * 成功（VM [TransactionSheetViewModel.saved]）→ [onDismiss] 关闭；校验/保存失败经 VM
 * [TransactionSheetViewModel.snackbar] 内联 SnackbarHost 展示，不关闭。
 *
 * @param tx 编辑目标（null=新增）；由编排者按导航参数传入（本文件不直接读 SavedStateHandle）。
 * @param onDismiss 关闭回调（保存成功/取消均回调）。
 * @param viewModel 注入的 VM（默认 `hiltViewModel()`，编排者也可传入自定义作用域实例）。
 */
@Composable
fun TransactionSheet(
    tx: Transaction? = null,
    onDismiss: () -> Unit,
    viewModel: TransactionSheetViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val amountFen by viewModel.amountFen.collectAsStateWithLifecycle()
    val categoryId by viewModel.categoryId.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 打开/切换目标时初始化：编辑回填 or 新增重置
    LaunchedEffect(tx) {
        if (tx != null) viewModel.initEdit(tx.id) else viewModel.initNew()
    }

    // snackbar：展示后立即清除（防重复触发）
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // 保存成功 → 关闭弹层（统一先清 snackbar 再关闭，防止 Sheet 移出组合时残留「已保存」下次重放）
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismissAndClear: () -> Unit = {
        viewModel.clearSnackbar()
        currentOnDismiss()
    }
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { dismissAndClear() }
    }

    // 当前所选分类展示用（分类随 type 重订阅后即可命中）
    val selectedCategory = remember(categories, categoryId) {
        categories.firstOrNull { it.id == categoryId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SheetFormScaffold(
            title = if (isEditMode) "编辑记一笔" else "记一笔",
            onConfirm = { viewModel.save() },
            onDismiss = dismissAndClear,
            confirmEnabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TransactionTypeTab(
                selected = type,
                onSelect = { selected -> selected?.let(viewModel::setType) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            MoneyInputField(
                fen = amountFen,
                onFenChange = viewModel::setAmount,
                label = "金额",
            )

            Spacer(Modifier.height(12.dp))

            // 分类选择入口（选中分类展示 icon + 名称）
            Surface(
                onClick = { showCategoryPicker = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedCategory != null) {
                        Text(selectedCategory.icon, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            selectedCategory.name,
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

            Spacer(Modifier.height(12.dp))

            DatePickerField(
                date = date,
                onDateChange = viewModel::setDate,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { new -> if (new.length <= Validators.MAX_NOTE_LENGTH) viewModel.setDescription(new) },
                label = { Text("备注") },
                supportingText = { Text("${description.length}/${Validators.MAX_NOTE_LENGTH}") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isEditMode) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除")
                }
            }
        }

        // 校验/失败提示浮于底部按钮上方
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }

    // 分类选择弹层（复用既有 CategoryPickerSheet，按 type 过滤 + 初始选中）
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            type = type.toCategoryType(),
            excludeId = null,
            initialId = categoryId,
            onPick = { category ->
                viewModel.setCategoryId(category.id)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // 编辑态删除强确认
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除这笔记录",
            message = "确定删除这笔交易吗？删除后不可恢复。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
