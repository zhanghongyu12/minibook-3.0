package com.minibook.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.LoadingState
import com.minibook.ui.search.components.DebtConditions
import com.minibook.ui.search.components.SearchTabBar
import com.minibook.ui.search.components.TransactionConditions

/**
 * 搜索页（TASK-033，UI §8.1 P-12 / F-06 / DEC-013，不含 NavHost 接线）。
 *
 * 页面结构：
 * - `TopAppBar`「搜索」＋ 返回 ← ＋「重置」（清空当前组条件）；
 * - 条件区（Column，可滚动）：`SegmentedButton`「交易 | 债务」切换，展示当前组的条件字段；
 * - 结果区（LazyColumn）：交易 / 债务两组、组内时间倒序、行复用 [com.minibook.ui.components.TransactionListItem]/
 *   [com.minibook.ui.components.DebtDetailRow]（只读点击 → 根图全屏详情页）；空态 / min>max 错误态复用
 *   [com.minibook.ui.components.EmptyState]。
 *
 * 范围限当前账本（SearchViewModel 经两个 UseCase 内置账本切换重订阅，UI 不感知账本）。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param onOpenTransactionDetail 打开交易详情页回调（根图 `navigate(transaction-detail?id=…)`，查看态）。
 * @param onOpenDebtDetail 打开债务详情页回调（根图 `navigate(debt-detail?id=…)`，查看态）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTransactionDetail: (String) -> Unit = {},
    onOpenDebtDetail: (String) -> Unit = {},
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

    // 页内状态：分类选择弹层（页面级）；交易/债务明细均改走根图全屏详情页。
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
                        onDebtClick = { onOpenDebtDetail(it.id) },
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

/** 交易类型 → 分类类型（同为不同枚举，分类选择器按类型过滤用）。 */
private fun TransactionType.toCategoryType(): com.minibook.domain.model.CategoryType = when (this) {
    TransactionType.EXPENSE -> com.minibook.domain.model.CategoryType.EXPENSE
    TransactionType.INCOME -> com.minibook.domain.model.CategoryType.INCOME
}