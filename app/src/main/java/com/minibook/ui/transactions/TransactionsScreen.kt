package com.minibook.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.Transaction
import com.minibook.ui.components.DateSectionHeader
import com.minibook.ui.sheets.TransactionSheet
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.PrimaryFAB
import com.minibook.ui.components.TransactionListItem
import com.minibook.ui.components.TransactionTypeTab

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
    // 页内 Sheet 弹起状态：不走 NavHost route，Sheet 直接接收领域对象（编辑态 tx）。
    var showTransactionSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            PrimaryFAB(onClick = {
                editingTx = null
                showTransactionSheet = true
            })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TransactionTypeTab(
                selected = filterType,
                onSelect = viewModel::setFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    onClearFilter = { viewModel.setFilter(null) },
                )
                is TransactionsUiState.Success -> TransactionList(
                    transactions = state.transactions,
                    categoriesById = state.categoriesById,
                    onItemClick = { tx ->
                        editingTx = tx
                        showTransactionSheet = true
                    },
                )
            }
        }
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
 * 非空交易列表：按 `date` 倒序分组渲染。
 *
 * 分组在 UI 层用 `remember(transactions)` 完成（VM 只提供扁平列表）；组头 key 加
 * `header_` 前缀与行 key（`id`）隔离，`animateItem` 提供条目增删/移动的位移动画。
 *
 * @param transactions 扁平列表（DAO 已按 createdAt DESC 排序，故组内天然倒序）。
 * @param categoriesById 分类 id→[Category] 映射（[TransactionsViewModel] 联表产物），按
 *   `tx.categoryId` 解析名称/图标/颜色；分类已删（map 取不到）时 [TransactionListItem]
 *   fallback（❔/outline 色），对齐首页 0c041a3。
 * @param onItemClick 行点击回调（跳编辑态）。
 */
@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    categoriesById: Map<String, Category>,
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

/**
 * 空态：按是否有筛选分两种文案，有筛选时提供「清除筛选」动作。
 *
 * @param hasActiveFilter true=当前处于类型筛选态（展示清除动作）；false=无任何筛选。
 * @param onClearFilter 「清除筛选」回调（[com.minibook.ui.transactions.TransactionsViewModel.setFilter] null）。
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
