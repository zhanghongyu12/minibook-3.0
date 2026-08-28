package com.minibook.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.DateSectionHeader
import com.minibook.ui.components.dateToUtcMillis
import com.minibook.ui.components.utcMillisToDate
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.PrimaryFAB
import com.minibook.ui.components.SwipeToRevealContainer
import com.minibook.ui.components.TransactionListItem
import com.minibook.ui.components.TransactionTypeTab
import com.minibook.ui.navigation.BillsFilterBus
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 收支记录页（TASK-024，UI §2 P-03）。
 *
 * 页面结构：顶部 [TransactionTypeTab] 类型筛选（全部/支出/收入，切筛选即由 ViewModel
 * 重订阅）+ 中部三态内容区 + 右下 [PrimaryFAB] 新增入口（跳全屏交易详情页）。
 *
 * 列表按 `date` 分组（组头 [DateSectionHeader]，组内 createdAt DESC 由 DAO 保证），
 * `LazyColumn + key=id + animateItem` 保证千条级滚动流畅与条目复用稳定。分类信息经
 * [TransactionsViewModel] 联表（`Success.categoriesById`，id→[Category]，对齐首页 0c041a3），
 * [TransactionListItem] 按 `tx.categoryId` 取真实 `categoryName/categoryIcon/categoryColor`；
 * 分类已删（map 取不到）传 null 走组件 fallback（❔/outline 色），不崩溃。
 *
 * FAB / 行点击已接线：经 [onOpenTransaction] 跳根图全屏交易详情页（P-07，新增/查看/编辑三合一），
 * 不再页内弹 [androidx.compose.material3.ModalBottomSheet]。
 *
 * @param onBack 可选返回回调。非空时（根图明细态，如统计页跳转来的账单明细）页顶显示返回按钮（←），
 *   点击触发 [onBack]；为空时（嵌套 Tab 态，底部导航「记账」入口）保持现状无返回按钮。
 * @param onOpenTransaction 打开交易详情页回调（null=新增 / 非空=查看该 id）；由 MainScreen 注入根图导航。
 * @param onOpenTransactionEdit 打开交易详情页并直接进编辑态（列表长按菜单「编辑」）；由 MainScreen 注入根图导航。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun TransactionsScreen(
    onBack: (() -> Unit)? = null,
    onOpenTransaction: (String?) -> Unit = {},
    onOpenTransactionEdit: (String) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val filterCategoryId by viewModel.filterCategoryId.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val dateFrom by viewModel.dateFrom.collectAsStateWithLifecycle()
    val dateTo by viewModel.dateTo.collectAsStateWithLifecycle()
    // 报表分类占比点击 → 页面组合（切到记账 Tab）后才消费账单筛选请求：
    // 先由 MainScreen 观察 Bus 切 Tab，再由本组合作用域消费并 clear，避免 VM 常驻抢跑（时序 bug 修复）。
    LaunchedEffect(viewModel) {
        BillsFilterBus.request.collect { req ->
            if (req != null) {
                viewModel.applyBillsFilter(req)
                BillsFilterBus.clear()
            }
        }
    }
    // 批量管理模式（页内自持，不落 VM）：selectedIds=选中 id 集合，batchMode=是否处于批量模式。
    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    // 单条删除目标（长按菜单「删除」/ 左滑「删除」→ 强确认后删除）。
    var deleteTarget by remember { mutableStateOf<Transaction?>(null) }
    val scope = rememberCoroutineScope()

    // 当前筛选结果的交易 id 集合：批量「全选/取消全选」作用域（分类/类型筛选在批量模式下仍生效）。
    val allVisibleIds = (uiState as? TransactionsUiState.Success)?.transactions?.map { it.id }.orEmpty()

    /** 退出批量模式：清空选中、关闭确认框（删除完成后 / 取消时均走此路径）。 */
    fun exitBatchMode() {
        batchMode = false
        selectedIds = emptySet()
        showDeleteConfirm = false
    }

    /** 全选/取消全选：已全选当前可见 → 清空；否则选中当前全部可见。 */
    fun toggleSelectAll() {
        val allSelected = allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedIds }
        selectedIds = if (allSelected) emptySet() else allVisibleIds.toSet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            // 批量模式下隐藏 FAB（行点击被复用为「选中/取消选中」，无法再新增）。
            if (!batchMode) {
                PrimaryFAB(onClick = { onOpenTransaction(null) })
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 顶部栏：普通态仅返回按钮（批量管理入口已移到长按菜单「批量管理」）；批量态替换为
            // 批量操作栏（已选 N 条 + 改分类/改类型/删除/取消，全选已下放到列表上方）。
            if (batchMode) {
                BatchModeBar(
                    selectedCount = selectedIds.size,
                    onCancel = ::exitBatchMode,
                    onDelete = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                    onChangeCategory = { if (selectedIds.isNotEmpty()) showCategoryPicker = true },
                    onChangeType = { if (selectedIds.isNotEmpty()) showTypePicker = true },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Text(
                                "←",
                                fontSize = 22.sp,
                                modifier = Modifier.semantics { contentDescription = "返回" },
                            )
                        }
                    }
                }
            }
            TransactionTypeTab(
                selected = filterType,
                onSelect = viewModel::setFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            QueryFilterArea(
                remark = remark ?: "",
                onRemarkChange = viewModel::setRemark,
                dateFrom = dateFrom,
                dateTo = dateTo,
                onDateFromChange = { from -> viewModel.setDateRange(from, dateTo) },
                onDateToChange = { to -> viewModel.setDateRange(dateFrom, to) },
                categories = categories,
                selectedCategoryId = filterCategoryId,
                onCategorySelect = viewModel::setCategoryFilter,
                onReset = viewModel::clearFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            // 批量模式：全选行紧贴列表上方（Checkbox「全选」，作用于当前筛选结果 allVisibleIds）。
            if (batchMode) {
                SelectAllRow(
                    checked = allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedIds },
                    onToggle = ::toggleSelectAll,
                )
            }
            when (val state = uiState) {
                TransactionsUiState.Loading -> LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
                is TransactionsUiState.Error -> ErrorTransactions(
                    message = state.message,
                    onRetry = viewModel::refresh,
                )
                is TransactionsUiState.Empty -> EmptyTransactions(
                    hasActiveFilter = state.hasActiveFilter,
                    onClearFilter = viewModel::clearFilter,
                )
                is TransactionsUiState.Success -> TransactionList(
                    transactions = state.transactions,
                    categoriesById = state.categoriesById,
                    batchMode = batchMode,
                    selectedIds = selectedIds,
                    onToggleSelect = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onItemClick = { tx -> onOpenTransaction(tx.id) },
                    onEditClick = { tx -> onOpenTransactionEdit(tx.id) },
                    onDeleteClick = { tx -> deleteTarget = tx },
                    onBatchManage = {
                        batchMode = true
                        selectedIds = emptySet()
                    },
                )
            }
        }
    }

    // 批量删除确认（删除选中的 N 笔交易？）：确认后调 VM 删除动作，成功即退出批量模式。
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除确认") },
            text = { Text("删除选中的 ${selectedIds.size} 笔交易？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.deleteTransactions(selectedIds.toList())
                            exitBatchMode()
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    // 批量改分类：复用 [CategoryPickerSheet]（与记一笔/编辑同款分类选择组件），选中后批量改 + 退出批量模式。
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            type = null,
            excludeId = null,
            initialId = null,
            title = "改分类",
            onPick = { category ->
                showCategoryPicker = false
                scope.launch {
                    viewModel.batchUpdateCategory(selectedIds.toList(), category.id)
                    exitBatchMode()
                }
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // 批量改类型：支出/收入二选（底部动作条），提示改类型后分类归「其他」；确认后批量改 + 退出批量模式。
    if (showTypePicker) {
        TypePickerSheet(
            onPick = { type ->
                showTypePicker = false
                scope.launch {
                    viewModel.batchUpdateType(selectedIds.toList(), type)
                    exitBatchMode()
                }
            },
            onDismiss = { showTypePicker = false },
        )
    }

    // 单条删除强确认（长按菜单「删除」/ 左滑「删除」）：确认后调 VM 删除，列表 Flow 自动刷新。
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除这笔交易",
            message = "确定删除这笔交易吗？删除后不可恢复。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                deleteTarget = null
                scope.launch { viewModel.deleteTransactions(listOf(target.id)) }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    }

/**
 * 批量操作栏（批量模式顶部，替换普通态「管理」入口）。
 *
 * 第一行：左「已选 N 条」计数；第二行：右「改分类」「改类型」「删除」（0 选中时三者禁用）
 * 与「取消」退出批量模式。「全选」已下放到列表上方的 [SelectAllRow]，作用于当前筛选结果
 * （见 [TransactionsScreen.allVisibleIds]）。
 */
@Composable
private fun BatchModeBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onChangeCategory: () -> Unit,
    onChangeType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已选 $selectedCount 条",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onChangeCategory, enabled = selectedCount > 0) { Text("改分类") }
            TextButton(onClick = onChangeType, enabled = selectedCount > 0) { Text("改类型") }
            Button(onClick = onDelete, enabled = selectedCount > 0) { Text("删除") }
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

/**
 * 批量模式全选行（列表上方、紧贴列表）：左 [Checkbox]「全选」，整行可点切换，风格对齐
 * 批量态列表行的 Checkbox 形态。选中态 [checked] 由调用方按 [TransactionsScreen.allVisibleIds]
 * 计算（全选 = 当前筛选结果全部选中）。
 */
@Composable
private fun SelectAllRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text("全选", style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 批量改类型底部动作条：支出/收入二选，附「改类型后分类将归为「其他」」提示。
 * 纯 UI 组件（状态由 [TransactionsScreen] 页内自持），确认回调 [onPick] 传入所选类型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePickerSheet(
    onPick: (TransactionType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("改类型", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "改类型后分类将归为「其他」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onPick(TransactionType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                    Text("支出")
                }
                Button(onClick = { onPick(TransactionType.INCOME) }, modifier = Modifier.weight(1f)) {
                    Text("收入")
                }
            }
        }
    }
}

/**
 * 查询区（类型 Tab 之下，常驻显示，不随列表 Empty 消失）：
 * 模糊搜索输入框 + 「重置」、时间段起止选择（可单独清空）、收缩式分类筛选入口。
 *
 * 模糊搜索按备注/标题关键词（映射 description 列）即时过滤；时间段起止为 `yyyy-MM-dd`；
 * 分类筛选由横向 chip 收缩为单一「分类」按钮 + 下拉（含「全部分类」清除项）。
 */
@Composable
private fun QueryFilterArea(
    remark: String,
    onRemarkChange: (String) -> Unit,
    dateFrom: String?,
    dateTo: String?,
    onDateFromChange: (String?) -> Unit,
    onDateToChange: (String?) -> Unit,
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelect: (String?) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remarkState = rememberTextFieldState(remark)
    // 外部 remark（筛选状态）变化时同步到输入框（例如点「重置」清空）。
    LaunchedEffect(remark) {
        if (remarkState.text.toString() != remark) {
            remarkState.edit { replace(0, length, remark) }
        }
    }
    // 输入变化即时回写 VM（模糊过滤）。
    LaunchedEffect(remarkState) {
        snapshotFlow { remarkState.text.toString() }
            .collect { onRemarkChange(it) }
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                state = remarkState,
                textStyle = MaterialTheme.typography.labelMedium,
                placeholder = { Text("搜索备注/标题关键词", style = MaterialTheme.typography.labelMedium) },
                lineLimits = TextFieldLineLimits.SingleLine,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp), // 与第二行日期/分类控件统一 40dp 高度
            )
            TextButton(onClick = onReset) { Text("重置") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateFilterChip(
                placeholder = "起始日期",
                value = dateFrom,
                onChange = onDateFromChange,
                modifier = Modifier.weight(1f),
            )
            Text("~", color = MaterialTheme.colorScheme.onSurfaceVariant)
            DateFilterChip(
                placeholder = "结束日期",
                value = dateTo,
                onChange = onDateToChange,
                modifier = Modifier.weight(1f),
            )
            CategoryFilterEntry(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onSelect = onCategorySelect,
            )
        }
    }
}

/**
 * 单个日期筛选 chip（起/止日期）：展示 `yyyy-MM-dd` 或占位文案，点击弹 [DatePickerDialog]；
 * 已设值时右侧「✕」可单独清空（回调 null）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChip(
    placeholder: String,
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = remember(value) { value?.let { dateToUtcMillis(it) } }
    Surface(
        onClick = { showPicker = true },
        shape = MaterialTheme.shapes.small,
        color = if (value != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.labelSmall,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { onChange(null) },
                )
            }
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    utcMillisToDate(pickerState.selectedDateMillis)?.let(onChange)
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * 收缩式分类筛选入口：单一「分类」按钮显示当前分类名（或「全部分类」），点击弹
 * [DropdownMenu]，首项「全部分类」清除 + 按支出/收入分组的全部分类（复用 [CategoryDropdownSection]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterEntry(
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedCategoryId }?.name
    val contentColor = if (selectedCategoryId != null) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = if (selectedCategoryId != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (selectedCategoryId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("分类：", style = MaterialTheme.typography.labelSmall, color = contentColor)
                Text(
                    selectedName ?: "全部分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("▾", fontSize = 14.sp, color = contentColor)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(200.dp)
                .heightIn(max = 320.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DropdownMenuItem(
                    text = { Text("全部分类") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    },
                )
                CategoryDropdownSection(
                    title = "支出",
                    items = categories.filter { it.type == CategoryType.EXPENSE },
                    selectedCategoryId = selectedCategoryId,
                    onPick = { id ->
                        expanded = false
                        onSelect(id)
                    },
                )
                CategoryDropdownSection(
                    title = "收入",
                    items = categories.filter { it.type == CategoryType.INCOME },
                    selectedCategoryId = selectedCategoryId,
                    onPick = { id ->
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

/**
 * 下拉菜单分组：组标题 + 该组分类项（emoji + 名称），选中项右侧主题 primary「✓」。
 * 分组无分类时整组不渲染（避免空标题悬挂）。
 */
@Composable
private fun CategoryDropdownSection(
    title: String,
    items: List<Category>,
    selectedCategoryId: String?,
    onPick: (String) -> Unit,
) {
    if (items.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    items.forEach { category ->
        DropdownMenuItem(
            text = {
                Text(
                    "${category.icon} ${category.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = if (category.id == selectedCategoryId) {
                { Text("✓", color = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
            onClick = { onPick(category.id) },
        )
    }
}

/**
 * 非空交易列表：按 `date` 倒序分组渲染。
 *
 * 分组在 UI 层用 `remember(transactions)` 完成（VM 只提供扁平列表）；组头 key 加
 * `header_` 前缀与行 key（`id`）隔离，`animateItem` 提供条目增删/移动的位移动画。
 *
 * @param transactions 扁平列表（DAO 已按 createdAt DESC 排序，故组内天然倒序）。
 * @param categoriesById 分类 id→[Category] 映射（[TransactionsViewModel] 联表产物），按
 *   `tx.categoryId` 解析名称/图标/颜色；分类已删（map 取不到）时 [TransactionListItem]
 *   fallback（❔/outline 色），对齐首页 0c041a3。
 * @param batchMode 是否批量管理模式；true 时行前显示 [Checkbox]、行点击切换选中而非编辑。
 * @param selectedIds 当前选中 id 集合。
 * @param onToggleSelect 切换某条选中态回调。
 * @param onItemClick 行点击回调（普通态跳查看态；批量态不触达）。
 * @param onEditClick 长按菜单「编辑」回调（跳详情页编辑态）。
 * @param onDeleteClick 长按菜单「删除」/ 左滑「删除」回调（触发单条删除强确认）。
 * @param onBatchManage 长按菜单「批量管理」回调（进入批量管理模式）。
 */
@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    categoriesById: Map<String, Category>,
    batchMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onItemClick: (Transaction) -> Unit,
    onEditClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit,
    onBatchManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(transactions) {
        transactions.groupBy { it.date }.toSortedMap(reverseOrder())
    }
    // 单实例展开：当前左滑展开的交易 id（至多一行展开），滑另一行时旧行自动回收。
    var expandedId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // 预留 FAB 浮层空间，末条不被遮挡
    ) {
        grouped.forEach { (date, txs) ->
            item(key = "header_$date") {
                DateSectionHeader(date = date)
            }
            items(items = txs, key = { it.id }) { tx ->
                val category = categoriesById[tx.categoryId]
                if (batchMode) {
                    // 批量态：Checkbox 与整行点击均切换选中（复用 animateItem 保持动画）。
                    Row(
                        modifier = Modifier.animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tx.id in selectedIds,
                            onCheckedChange = { onToggleSelect(tx.id) },
                        )
                        TransactionListItem(
                            tx = tx,
                            categoryName = category?.name,
                            categoryIcon = category?.icon,
                            categoryColor = category?.color,
                            onClick = { onToggleSelect(tx.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    TransactionRow(
                        tx = tx,
                        category = category,
                        onClick = { onItemClick(tx) },
                        onEdit = { onEditClick(tx) },
                        onDelete = { onDeleteClick(tx) },
                        onBatchManage = onBatchManage,
                        expanded = expandedId == tx.id,
                        onExpand = { expandedId = tx.id },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * 普通态交易行：左滑露出「删除」+ 长按弹「编辑/删除/批量管理」菜单。
 *
 * 长按与点击分离：onClick 仍打开查看态，onLongClick 弹屏幕居中 [Dialog] 菜单。左滑删除按钮
 * 点击后只触发外层强确认弹窗（[onDelete]），确认后才真正删除（删除不可恢复）。「批量管理」
 * 进入批量模式（对齐原顶部「管理」按钮行为）。批量模式下 [TransactionList] 走 Checkbox 分支，
 * 不经此行，避免与批量勾选手势冲突。
 */
@Composable
private fun TransactionRow(
    tx: Transaction,
    category: Category?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBatchManage: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpand: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    SwipeToRevealContainer(
        onDeleteClick = onDelete,
        modifier = modifier,
        expanded = expanded,
        onExpandRequest = onExpand,
    ) {
        Box {
            TransactionListItem(
                tx = tx,
                categoryName = category?.name,
                categoryIcon = category?.icon,
                categoryColor = category?.color,
                onClick = onClick,
                onLongClick = { showMenu = true },
            )
        }
    }

    // 长按菜单：屏幕居中 Dialog（替代锚定行的 DropdownMenu），选项点击后先关菜单再执行。
    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    TextButton(
                        onClick = { showMenu = false; onEdit() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("编辑") }
                    TextButton(
                        onClick = { showMenu = false; onDelete() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = { showMenu = false; onBatchManage() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("批量管理") }
                    TextButton(
                        onClick = { showMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("取消") }
                }
            }
        }
    }
}

/**
 * 空态：按是否有筛选分两种文案，有筛选时提供「清除筛选」动作。
 *
 * @param hasActiveFilter true=当前处于筛选态（类型或分类任一非空，展示清除动作）；false=无任何筛选。
 * @param onClearFilter 「清除筛选」回调（[com.minibook.ui.transactions.TransactionsViewModel.clearFilter]，
 *   一并复位类型 + 分类）。
 */
@Composable
private fun EmptyTransactions(
    hasActiveFilter: Boolean,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasActiveFilter) {
        EmptyState(
            icon = EmptyReceiptIcon,
            title = "没有符合条件的交易",
            subtitle = "换个筛选条件试试",
            actionLabel = "清除筛选",
            onAction = onClearFilter,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        EmptyState(
            icon = EmptyReceiptIcon,
            title = "暂无交易记录",
            subtitle = "点击右下角「＋」记一笔",
            modifier = modifier.fillMaxSize(),
        )
    }
}

/**
 * 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 「重试」按钮。
 *
 * @param message 错误文案（VM 已折叠）。
 * @param onRetry 「重试」回调（[com.minibook.ui.transactions.TransactionsViewModel.refresh]）。
 */
@Composable
private fun ErrorTransactions(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyReceiptIcon,
        title = message,
        subtitle = "加载失败，请重试",
        actionLabel = "重试",
        onAction = onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 空态图标：手写「小票」ImageVector。
 *
 * TASK-020 偏差：工程未引入 material-icons 库（MainScreen 同款注记），[EmptyState] 又要求
 * 注入 [ImageVector]，故用 [addPathNodes] 解析 Material「receipt」path 数据本地构造，不新增
 * 依赖、不改 toml/build.gradle。图标为纯填充矢量，tint 由 [EmptyState] 内 `Icon(tint=...)`
 * 统一上色，此处 fill 色仅占位（Black，不参与渲染）。
 */
private val EmptyReceiptIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyTransactions",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2z" +
                "M3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22" +
                "l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2" +
                " 7.5 3.5 6 2 4.5 3.5 3 2v20z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
