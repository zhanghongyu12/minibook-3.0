package com.minibook.ui.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.DebtDetailRow
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.TransactionListItem
import com.minibook.ui.components.TransactionTypeTab
import com.minibook.util.TimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 搜索页（TASK-033，UI §8.1 P-12 / F-06 / DEC-013，不含 NavHost 接线）。
 *
 * 页面结构：
 * - `TopAppBar`「搜索」＋ 返回 ← ＋「重置」（清空当前组条件）；
 * - 条件区（Column，可滚动）：`SegmentedButton`「交易 | 债务」切换，展示当前组的条件字段；
 * - 结果区（LazyColumn）：交易 / 债务两组、组内时间倒序、行复用 [TransactionListItem]/
 *   [DebtDetailRow]（只读点击 → 只读明细 Sheet）；空态 / min>max 错误态复用 [EmptyState]。
 *
 * 范围限当前账本（SearchViewModel 经两个 UseCase 内置账本切换重订阅，UI 不感知账本）。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param onOpenTransactionDetail 打开交易详情页回调（根图 `navigate(transaction-detail?id=…)`，查看态）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTransactionDetail: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val amountRangeInvalid by viewModel.amountRangeInvalid.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    // 交易组条件
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val amountMinFen by viewModel.amountMinFen.collectAsStateWithLifecycle()
    val amountMaxFen by viewModel.amountMaxFen.collectAsStateWithLifecycle()
    val categoryId by viewModel.categoryId.collectAsStateWithLifecycle()
    val dateFrom by viewModel.dateFrom.collectAsStateWithLifecycle()
    val dateTo by viewModel.dateTo.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    // 债务组条件
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()

    // 页内状态：债务只读明细 Sheet / 分类选择弹层（页面级）；交易明细改走根图全屏详情页。
    var detailDebt by remember { mutableStateOf<Debt?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val selectedCategory = remember(categories, categoryId) {
        categories.firstOrNull { it.id == categoryId }
    }
    val rangeErrorMessage = if (amountRangeInvalid) SearchViewModel.AMOUNT_RANGE_ERROR_MESSAGE else null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchTopBar(onBack, onReset = viewModel::resetCurrentTab)
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 条件区（Column，可滚动）
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                SearchTabBar(tab = tab, onSelect = viewModel::setTab)
                when (tab) {
                    SearchTab.TRANSACTION -> TransactionConditions(
                        remark = remark,
                        onRemarkChange = viewModel::setRemark,
                        amountMinFen = amountMinFen,
                        onAmountMinFenChange = viewModel::setAmountMinFen,
                        amountMaxFen = amountMaxFen,
                        onAmountMaxFenChange = viewModel::setAmountMaxFen,
                        amountRangeInvalid = amountRangeInvalid,
                        rangeErrorMessage = rangeErrorMessage,
                        selectedCategory = selectedCategory,
                        onCategoryClick = { showCategoryPicker = true },
                        dateFrom = dateFrom,
                        onDateFromChange = viewModel::setDateFrom,
                        dateTo = dateTo,
                        onDateToChange = viewModel::setDateTo,
                        type = type,
                        onTypeChange = viewModel::setType,
                    )
                    SearchTab.DEBT -> DebtConditions(
                        partyName = partyName,
                        onPartyNameChange = viewModel::setPartyName,
                        direction = direction,
                        onDirectionChange = viewModel::setDirection,
                    )
                }
            }

            // 结果区（LazyColumn）
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val state = uiState) {
                    SearchUiState.Loading -> LoadingState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                    is SearchUiState.Empty -> EmptySearch(
                        hasActiveFilter = state.hasActiveFilter,
                        onClear = viewModel::clearAll,
                    )
                    is SearchUiState.Error -> ErrorSearch(
                        message = state.message,
                        onRetry = if (state.message == SearchViewModel.AMOUNT_RANGE_ERROR_MESSAGE) {
                            null
                        } else {
                            viewModel::refresh
                        },
                    )
                    is SearchUiState.Success -> SearchResults(
                        transactions = state.transactions,
                        debts = state.debts,
                        categoriesById = state.categoriesById,
                        onTxClick = { onOpenTransactionDetail(it.id) },
                        onDebtClick = { detailDebt = it },
                    )
                }
            }
        }
    }

    // 分类筛选弹层（C-02，type 随下方类型切换）
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            type = type?.toCategoryType(),
            excludeId = null,
            initialId = categoryId,
            onPick = { category ->
                viewModel.setCategoryId(category.id)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // 只读明细 Sheet：债务
    detailDebt?.let { debt ->
        DebtDetailSheet(
            debt = debt,
            onDismiss = { detailDebt = null },
        )
    }
}

/**
 * 搜索顶栏：标题「搜索」＋ 返回 ← ＋「重置」。
 *
 * material-icons 缺失（TASK-020 偏差），返回图标用 `Text("←")` 占位（对齐 LedgerManageScreen）。
 *
 * @param onBack 返回回调。
 * @param onReset 「重置」回调（清空当前组全部条件）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(onBack: () -> Unit, onReset: () -> Unit) {
    TopAppBar(
        title = { Text("搜索") },
        navigationIcon = {
            IconButton(onClick = onBack) { Text("←") }
        },
        actions = {
            TextButton(onClick = onReset) { Text("重置") }
        },
    )
}

/** 条件组切换：「交易 | 债务」SegmentedButton（UI §8.1）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTabBar(tab: SearchTab, onSelect: (SearchTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = tab == SearchTab.TRANSACTION,
            onClick = { onSelect(SearchTab.TRANSACTION) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("交易") },
        )
        SegmentedButton(
            selected = tab == SearchTab.DEBT,
            onClick = { onSelect(SearchTab.DEBT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("债务") },
        )
    }
}

/** 交易组条件（UI §8.1）：备注关键词 / 金额区间 / 分类 / 日期区间 / 类型。 */
@Composable
private fun TransactionConditions(
    remark: String,
    onRemarkChange: (String) -> Unit,
    amountMinFen: Long?,
    onAmountMinFenChange: (Long?) -> Unit,
    amountMaxFen: Long?,
    onAmountMaxFenChange: (Long?) -> Unit,
    amountRangeInvalid: Boolean,
    rangeErrorMessage: String?,
    selectedCategory: Category?,
    onCategoryClick: () -> Unit,
    dateFrom: String?,
    onDateFromChange: (String?) -> Unit,
    dateTo: String?,
    onDateToChange: (String?) -> Unit,
    type: TransactionType?,
    onTypeChange: (TransactionType?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = remark,
            onValueChange = onRemarkChange,
            label = { Text("备注包含…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // 金额区间：MinFenField + ~ + MaxFenField（C-01 ×2，元↔分）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyInputField(
                fen = amountMinFen,
                onFenChange = onAmountMinFenChange,
                label = "最小金额",
                isError = amountRangeInvalid,
                errorMessage = rangeErrorMessage,
                modifier = Modifier.weight(1f),
            )
            Text("~", modifier = Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyInputField(
                fen = amountMaxFen,
                onFenChange = onAmountMaxFenChange,
                label = "最大金额",
                isError = amountRangeInvalid,
                errorMessage = rangeErrorMessage,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        CategoryFilterChip(category = selectedCategory, onClick = onCategoryClick)

        Spacer(Modifier.height(8.dp))

        // 日期区间：起 / 止（C-10 ×2，可清空）
        Row(modifier = Modifier.fillMaxWidth()) {
            OptionalDateField(
                label = "起",
                date = dateFrom,
                onDateChange = onDateFromChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OptionalDateField(
                label = "止",
                date = dateTo,
                onDateChange = onDateToChange,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        TransactionTypeTab(
            selected = type,
            onSelect = onTypeChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))
    }
}

/** 债务组条件（UI §8.1）：姓名关键词 / 方向（借出·借入）。 */
@Composable
private fun DebtConditions(
    partyName: String,
    onPartyNameChange: (String) -> Unit,
    direction: DebtDirection?,
    onDirectionChange: (DebtDirection?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = partyName,
            onValueChange = onPartyNameChange,
            label = { Text("对方姓名包含…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // 方向：二选一 Card「借出 | 借入」（kind 口径 → DebtDirection，null=全部）
        DebtDirectionSelector(direction = direction, onSelect = onDirectionChange)

        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 分类筛选 Chip：未选 → 「全部分类」（surfaceVariant）；已选 → 分类 icon+名（primaryContainer）。
 * 点击弹 [CategoryPickerSheet]（C-02，type 随下方类型）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterChip(category: Category?, onClick: () -> Unit) {
    val selected = category != null
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(category?.icon ?: "🗂", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                category?.name ?: "全部分类",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 债务方向选择：二选一「借出 | 借入」Card（kind 口径）。
 *
 * 映射 [DebtDirection]：借出=OWE_ME / 借入=I_OWE；未选中=不过滤（再点一次取消）。呈 Card（Surface + 边框）
 * 以对齐 UI §8.1「二选一 Card」。标签语义词属纯展示映射。
 */
@Composable
private fun DebtDirectionSelector(direction: DebtDirection?, onSelect: (DebtDirection?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        DirectionOption(
            label = "借出",
            selected = direction == DebtDirection.OWE_ME,
            onClick = { onSelect(if (direction == DebtDirection.OWE_ME) null else DebtDirection.OWE_ME) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DirectionOption(
            label = "借入",
            selected = direction == DebtDirection.I_OWE,
            onClick = { onSelect(if (direction == DebtDirection.I_OWE) null else DebtDirection.I_OWE) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 方向单选项 Card（Surface + 边框 + 选中主色底）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Text(if (selected) "✓" else "", color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 可选日期字段（C-10 口径扩展，支持「未设置」+ 可清空）：Null 表示不过滤。
 * 复用 [Surface] 样式 + [DatePickerDialog]；展示 `yyyy-MM-dd`（经 [TimeFormatter.formatDate]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalDateField(
    label: String,
    date: String?,
    onDateChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                date?.let { TimeFormatter.formatDate(it) } ?: "未设置",
                style = MaterialTheme.typography.bodyLarge,
                color = if (date != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (date != null) {
                IconButton(
                    onClick = { onDateChange(null) },
                    modifier = Modifier.size(24.dp).semantics { contentDescription = "清除$label" },
                ) {
                    Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDialog) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateToUtcMillis(date))
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    utcMillisToDate(pickerState.selectedDateMillis)?.let(onDateChange)
                    showDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 结果区：交易 / 债务两组，组内时间倒序，行只读点击。 */
@Composable
private fun SearchResults(
    transactions: List<Transaction>,
    debts: List<Debt>,
    categoriesById: Map<String, Category>,
    onTxClick: (Transaction) -> Unit,
    onDebtClick: (Debt) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (transactions.isNotEmpty()) {
            item(key = "group_transaction") { GroupHeader(label = "交易", count = transactions.size) }
            items(items = transactions, key = { it.id }) { tx ->
                val category = categoriesById[tx.categoryId]
                TransactionListItem(
                    tx = tx,
                    categoryName = category?.name,
                    categoryIcon = category?.icon,
                    categoryColor = category?.color,
                    onClick = { onTxClick(tx) },
                )
            }
        }
        if (debts.isNotEmpty()) {
            item(key = "group_debt") { GroupHeader(label = "债务", count = debts.size) }
            items(items = debts, key = { it.id }) { debt ->
                DebtDetailRow(
                    debt = debt,
                    onClick = { onDebtClick(debt) },
                )
            }
        }
    }
}

/** 结果分组头（UI §8.1「分组头『交易 (N)』」）。 */
@Composable
private fun GroupHeader(label: String, count: Int) {
    Text(
        text = "$label ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 空态：无输入 → 引导输入；有条件但 0 命中 → 「没有匹配的记录」+ 清除条件。 */
@Composable
private fun EmptySearch(hasActiveFilter: Boolean, onClear: () -> Unit) {
    if (hasActiveFilter) {
        EmptyState(
            icon = SearchIcon,
            title = "没有匹配的记录",
            subtitle = "换个筛选条件试试",
            actionLabel = "清除条件",
            onAction = onClear,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        EmptyState(
            icon = SearchIcon,
            title = "输入关键词或设置筛选条件开始搜索",
            subtitle = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 错误态：min>max（无重试）或账本/加载异常（带重试）。 */
@Composable
private fun ErrorSearch(message: String, onRetry: (() -> Unit)?) {
    if (onRetry != null) {
        EmptyState(
            icon = SearchIcon,
            title = message,
            subtitle = "加载失败，请重试",
            actionLabel = "重试",
            onAction = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        EmptyState(
            icon = SearchIcon,
            title = message,
            subtitle = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 空态/错误态图标：手写「放大镜」ImageVector（material-icons 缺失，TASK-020 偏差同口径）。 */
private val SearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SearchIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 " +
                "5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z" +
                "M9.5 14C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5s4.5 2.01 4.5 4.5S11.99 14 9.5 14z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 交易类型 → 分类类型（同为不同枚举，分类选择器按类型过滤用）。 */
private fun TransactionType.toCategoryType(): com.minibook.domain.model.CategoryType = when (this) {
    TransactionType.EXPENSE -> com.minibook.domain.model.CategoryType.EXPENSE
    TransactionType.INCOME -> com.minibook.domain.model.CategoryType.INCOME
}

/** `yyyy-MM-dd`（可为 null）→ UTC 当日零点毫秒（DatePicker 用），null 回退今天。 */
private fun dateToUtcMillis(date: String?): Long {
    val localDate = date?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    } ?: LocalDate.now()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** DatePicker 选中毫秒（UTC 当日零点）→ `yyyy-MM-dd`；null 表示未选中。 */
private fun utcMillisToDate(millis: Long?): String? {
    if (millis == null) return null
    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return TimeFormatter.formatDate(localDate.toString())
}
