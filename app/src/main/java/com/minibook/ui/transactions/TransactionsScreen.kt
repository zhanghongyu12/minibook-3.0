package com.minibook.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.DateSectionHeader
import com.minibook.ui.sheets.TransactionSheet
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.PrimaryFAB
import com.minibook.ui.components.TransactionListItem
import com.minibook.ui.components.TransactionTypeTab
import kotlinx.coroutines.launch

/**
 * 收支记录页（TASK-024，UI §2 P-03）。
 *
 * 页面结构：顶部 [TransactionTypeTab] 类型筛选（全部/支出/收入，切筛选即由 ViewModel
 * 重订阅）+ 中部三态内容区 + 右下 [PrimaryFAB] 新增入口（弹 [TransactionSheet]）。
 *
 * 列表按 `date` 分组（组头 [DateSectionHeader]，组内 createdAt DESC 由 DAO 保证），
 * `LazyColumn + key=id + animateItem` 保证千条级滚动流畅与条目复用稳定。分类信息经
 * [TransactionsViewModel] 联表（`Success.categoriesById`，id→[Category]，对齐首页 0c041a3），
 * [TransactionListItem] 按 `tx.categoryId` 取真实 `categoryName/categoryIcon/categoryColor`；
 * 分类已删（map 取不到）传 null 走组件 fallback（❔/outline 色），不崩溃。
 *
 * FAB / 行点击已接线：页内状态弹 [TransactionSheet]（P-07，新增/编辑态），不走 NavHost route。
 *
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val filterCategoryId by viewModel.filterCategoryId.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val billsFilterLabel by viewModel.billsFilterLabel.collectAsStateWithLifecycle()
    // 页内 Sheet 弹起状态：不走 NavHost route，Sheet 直接接收领域对象（编辑态 tx）。
    var showTransactionSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    // 批量管理模式（页内自持，不落 VM）：selectedIds=选中 id 集合，batchMode=是否处于批量模式。
    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
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
                PrimaryFAB(onClick = {
                    editingTx = null
                    showTransactionSheet = true
                })
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 报表分类占比跳转后的筛选来源提示条（可「清除」复位全部筛选）。
            billsFilterLabel?.let { label ->
                BillsFilterBanner(label = label, onClear = viewModel::clearFilter)
            }
            // 顶部栏：普通态右侧「管理」入口；批量态替换为批量操作栏（已选 N 条 + 全选/删除/取消）。
            if (batchMode) {
                BatchModeBar(
                    selectedCount = selectedIds.size,
                    allSelected = allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedIds },
                    onToggleSelectAll = ::toggleSelectAll,
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
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        batchMode = true
                        selectedIds = emptySet()
                    }) {
                        Text("管理")
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
            CategoryFilterBar(
                categories = categories,
                selectedCategoryId = filterCategoryId,
                onSelect = viewModel::setCategoryFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
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
                    onItemClick = { tx ->
                        editingTx = tx
                        showTransactionSheet = true
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

    // 记一笔 / 编辑记一笔 Sheet（P-07）：页内状态驱动，tx=null 新增态 / tx 非空编辑态。
    if (showTransactionSheet) {
        TransactionSheet(
            tx = editingTx,
            onDismiss = { showTransactionSheet = false },
        )
    }
}

/**
 * 账单筛选提示条：报表分类占比点击跳转后，顶部展示「周期 · 分类」筛选来源 + 「清除」按钮。
 * 清除 = 复位类型/分类/日期区间/提示条全部筛选（走 [com.minibook.ui.transactions.TransactionsViewModel.clearFilter]）。
 */
@Composable
private fun BillsFilterBanner(
    label: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
}

/**
 * 批量操作栏（批量模式顶部，替换普通态「管理」入口）。
 *
 * 第一行：左「已选 N 条」计数，右「全选/取消全选」；第二行：右「改分类」「改类型」「删除」
 * （0 选中时三者禁用）与「取消」退出批量模式。分类/类型筛选在批量模式下仍生效，「全选」作用于
 * 当前筛选结果（见 [TransactionsScreen.allVisibleIds]）。
 */
@Composable
private fun BatchModeBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
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
            TextButton(onClick = onToggleSelectAll) {
                Text(if (allSelected) "取消全选" else "全选")
            }
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
 * 分类筛选栏（常驻显示，不随列表 Empty 消失）：横向可滚动 chip 行 + 右端下拉选择器。
 *
 * chip 行：首个「全部」chip（选中 = 清空分类筛选），其后每个分类一个 chip（emoji + 名称）；
 * 选中的 chip 以主题配色高亮（primaryContainer / onPrimaryContainer，对齐 SearchScreen 的
 * [CategoryFilterChip] 手写 Surface 风格，工程未引入 material-icons、分类 icon 为 emoji 文本）。
 *
 * 下拉选择器：chip 行右端一个筛选图标按钮（[CategoryFilterIcon]），点开 [DropdownMenu]
 * 按支出/收入分组列出全部分类（带 emoji），点击设同一 categoryId；选中某分类时下拉入口
 * 显示选中态（primaryContainer 底色）。chip 与下拉共用 [selectedCategoryId] / [onSelect]，
 * 清除分类筛选走「全部」chip。
 *
 * @param categories 候选分类（VM [com.minibook.ui.transactions.TransactionsViewModel.categories]，
 *   当前账本全类型，order 升序）。
 * @param selectedCategoryId 当前选中分类 id；null=全部分类。
 * @param onSelect 选中回调；null=清空分类筛选（「全部」chip）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterBar(
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 横向可滚动 chip 行：内容超宽时可滑动，下拉入口固定在右端不随 chip 滚动。
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryFilterChip(
                icon = null,
                name = "全部",
                selected = selectedCategoryId == null,
                onClick = { onSelect(null) },
            )
            categories.forEach { category ->
                CategoryFilterChip(
                    icon = category.icon,
                    name = category.name,
                    selected = category.id == selectedCategoryId,
                    onClick = { onSelect(category.id) },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        CategoryFilterDropdown(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onSelect = onSelect,
        )
    }
}

/**
 * 单个分类筛选 chip（手写 Surface，对齐 SearchScreen 的 [CategoryFilterChip] 配色口径）：
 * 选中 → primaryContainer/onPrimaryContainer + primary 描边；未选 → surfaceVariant/onSurfaceVariant。
 *
 * @param icon 分类 emoji 文本；null 表示「全部」chip（不展示图标）。
 * @param name chip 文案。
 * @param selected 是否选中。
 * @param onClick 点击回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterChip(
    icon: String?,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Text(icon, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 分类下拉选择器：筛选图标按钮 + [DropdownMenu]（按支出/收入分组，带 emoji + 选中标记）。
 *
 * 入口与 chip 联动：选中某分类时入口用 primaryContainer 底色 + onPrimaryContainer 图标
 * 呈现「选中态」。选中项在菜单内以主题 primary「✓」标记；点击分类即关闭菜单并回传同一
 * categoryId（清除分类筛选仍走「全部」chip，故菜单不重复提供「全部」项）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterDropdown(
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
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
                if (selectedCategoryId != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Icon(
                imageVector = CategoryFilterIcon,
                contentDescription = "分类筛选",
                tint = if (selectedCategoryId != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
            text = { Text("${category.icon} ${category.name}") },
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
 * 分类筛选图标：Material「filter_list」path 本地构造（工程未引入 material-icons，同
 * [EmptyReceiptIcon] 手法）。纯填充矢量，tint 由 [Icon] 统一上色，fill 仅占位（不参与渲染）。
 */
private val CategoryFilterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CategoryFilter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
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
 * @param onItemClick 行点击回调（普通态跳编辑态；批量态不触达）。
 */
@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    categoriesById: Map<String, Category>,
    batchMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onItemClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(transactions) {
        transactions.groupBy { it.date }.toSortedMap(reverseOrder())
    }
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
                    TransactionListItem(
                        tx = tx,
                        categoryName = category?.name,
                        categoryIcon = category?.icon,
                        categoryColor = category?.color,
                        onClick = { onItemClick(tx) },
                        modifier = Modifier.animateItem(),
                    )
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
