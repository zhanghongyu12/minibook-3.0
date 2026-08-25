package com.minibook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.EmptyState
import com.minibook.ui.sheets.TransactionSheet
import com.minibook.ui.components.LedgerSelector
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.NetBalanceCard
import com.minibook.ui.components.NetBalanceRow
import com.minibook.ui.components.PrimaryFAB
import com.minibook.ui.components.TransactionListItem
import com.minibook.ui.navigation.BillsFilterBus
import com.minibook.ui.navigation.BillsFilterRequest
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.ui.theme.lightSemanticColors
import com.minibook.util.TimeFormatter
import java.time.YearMonth

/**
 * 首页跨层导航桥（TASK-033/036/038 接线收口）。
 *
 * `search`/`reports`/`budgets` 三个路由注册于**根图** [MiniBookNavHost]，而首页 Home 位于
 * [MainScreen] 内嵌 5 Tab 嵌套图——嵌套图无法直达根图目的地，与 ledger-manage 同理需根图
 * `navigate` 中转。为在不穿透 [MainScreen]（在其边界外）的前提下把首页入口接上根路由，
 * 这里用 [LocalHomeNavigation] CompositionLocal 把根导航能力经 [MiniBookNavHost] 注入，
 * [HomeScreen] 只读取使用（参数式回调的分发由 MainScreen 承担，改动面更大，故弃用）。
 */
interface HomeNavigation {
    /** 打开搜索页（根图 `search`）。 */
    fun openSearch()

    /** 打开报表页（根图 `reports`）。 */
    fun openReports()

    /** 打开预算页（根图 `budgets`）。 */
    fun openBudgets()

    /** 打开账单导入页（根图 `import`，F-12）。 */
    fun openImport()
}

/**
 * 首页根导航桥的 CompositionLocal；由 [MiniBookNavHost] 在根图内注入真实实现，
 * 缺省提供 no-op（预览/独立 compose 不崩）。读取方 =[HomeScreen] 首页入口。
 */
val LocalHomeNavigation: androidx.compose.runtime.ProvidableCompositionLocal<HomeNavigation> =
    staticCompositionLocalOf {
        object : HomeNavigation {
            override fun openSearch() = Unit
            override fun openReports() = Unit
            override fun openBudgets() = Unit
            override fun openImport() = Unit
        }
    }

/**
 * 首页仪表板（TASK-023，UI §2 P-02，ARCHITECTURE §12/§8.3）。
 *
 * 顶部 [LedgerSelector]（当前账本名/图标）+ FAB「＋」（记一笔）+ 内容区三态渲染：
 * - [HomeUiState.Loading]：骨架屏（[LoadingState]）；
 * - [HomeUiState.Error]：一屏错误提示并引导账本管理（本期无跳转，仅文案）；
 * - [HomeUiState.Empty] / [HomeUiState.Success]：[HomeDashboardContent]——结余卡
 *   （[NetBalanceCard]「本月结余」+ 收入/支出明细行）+ 最近交易列表（[TransactionListItem]，
 *   前 10 条 UI 截断，A-3）+「查看全部」入口。
 *
 * **分类联表**：交易无分类名/图标/色，由 [HomeViewModel] 经 `ObserveCategoriesUseCase` 联表
 * （id→[Category]）折叠为 [HomeUiState.Success.categoriesById]；[HomeScreen] 据此给
 * [TransactionListItem] 传真实 `categoryName/categoryIcon/categoryColor`（缺失时组件 fallback ❔）。
 *
 * **导航已接线**：FAB / 空态「记一笔」/ 最近交易行点击 → 页内弹 [TransactionSheet]（P-07，
 * 新增/编辑态，由页内状态驱动不走 NavHost route）；LedgerSelector → 账本管理（P-11）；
 * 「查看全部」→ transactions（P-03，经 [onNavigateToTransactions] 由 MainScreen 嵌套导航）。
 *
 * @param viewModel 首页 VM；默认经 [hiltViewModel] 解析。
 * @param onNavigateToTransactions 「查看全部」回调（MainScreen 内嵌导航到 [Destination.Transactions]）。
 * @param onNavigateToLedgerManage 账本入口回调（MainScreen 中转根图导航到 ledger-manage）。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToLedgerManage: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    // 页内 Sheet 弹起状态：不走 NavHost route，Sheet 直接接收领域对象（编辑态 tx）。
    var showTransactionSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    // 首页跨层根导航（搜索/报表/预算）：由 MiniBookNavHost 经 LocalHomeNavigation 注入；
    // 当月预算超支红点（DEC-012 入口轻量提示）：观察 budgetOverBudget。
    val homeNavigation = LocalHomeNavigation.current
    val budgetOverBudget by viewModel.budgetOverBudget.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            HomeTopBar(
                uiState = uiState,
                onNavigateToLedgerManage = onNavigateToLedgerManage,
                onOpenSearch = homeNavigation::openSearch,
            )
        },
        floatingActionButton = {
            PrimaryFAB(onClick = {
                editingTx = null
                showTransactionSheet = true
            })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                HomeUiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(16.dp))
                is HomeUiState.Error -> HomeErrorContent(
                    message = state.message,
                    onRetry = viewModel::refresh,
                )
                is HomeUiState.Empty -> HomeDashboardContent(
                    month = selectedMonth,
                    balance = 0L,
                    income = 0L,
                    expense = 0L,
                    transactions = emptyList(),
                    categoriesById = emptyMap(),
                    onOpenNewTransaction = {
                        editingTx = null
                        showTransactionSheet = true
                    },
                    onOpenEditTransaction = { tx ->
                        editingTx = tx
                        showTransactionSheet = true
                    },
                    onNavigateToTransactions = onNavigateToTransactions,
                    onOpenReports = homeNavigation::openReports,
                    onOpenBudgets = homeNavigation::openBudgets,
                    onOpenImport = homeNavigation::openImport,
                    budgetOverBudget = budgetOverBudget,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                )
                is HomeUiState.Success -> HomeDashboardContent(
                    month = state.month,
                    balance = state.balance,
                    income = state.income,
                    expense = state.expense,
                    transactions = state.recentTransactions,
                    categoriesById = state.categoriesById,
                    onOpenNewTransaction = {
                        editingTx = null
                        showTransactionSheet = true
                    },
                    onOpenEditTransaction = { tx ->
                        editingTx = tx
                        showTransactionSheet = true
                    },
                    onNavigateToTransactions = onNavigateToTransactions,
                    onOpenReports = homeNavigation::openReports,
                    onOpenBudgets = homeNavigation::openBudgets,
                    onOpenImport = homeNavigation::openImport,
                    budgetOverBudget = budgetOverBudget,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
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
 * 顶部栏：左「首页」标题 + 右搜索入口（🔍）+ [LedgerSelector]（UI §2 P-02 顶栏；状态栏
 * inset 自处理）。material-icons 缺失（TASK-020 偏差），搜索入口同全工程用 emoji 字符占位。
 *
 * @param onNavigateToLedgerManage [LedgerSelector] 点击回调（跳账本管理，P-11）。
 * @param onOpenSearch 搜索入口回调（根图导航到 `search`，§8.6「首页顶部搜索入口」）。
 */
@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    onNavigateToLedgerManage: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLedger = (uiState as? HomeUiState.Success)?.currentLedger
        ?: (uiState as? HomeUiState.Empty)?.currentLedger
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "首页",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSearch) {
            Text("🔍", fontSize = 20.sp)
        }
        LedgerSelector(
            ledger = currentLedger,
            onClick = onNavigateToLedgerManage,
        )
    }
}

/**
 * 首页仪表板主体内容（[HomeUiState.Empty] / [HomeUiState.Success] 共用）：
 * 结余卡（[MonthBalanceCard]，所选月份 + 左右滑动/箭头切换）+「最近交易」区（列表或空态）。
 *
 * @param month 所选月份（结余卡标题 + 切换；Empty 态来自 `selectedMonth`、Success 态来自 `Success.month`）。
 * @param balance 所选月结余（分，可负）；负值经 [NetBalanceCard]/[MoneyText] 红色展示。
 * @param income 所选月收入（分）。
 * @param expense 所选月支出（分）。
 * @param transactions 所选月最近交易（调用方已截断前 10，A-3；此处再 take 兜底）。
 * @param categoriesById 分类 id→[Category] 映射（[HomeViewModel] 联表产物），按 `tx.categoryId`
 *   解析名称/图标/颜色；缺失分类时 [TransactionListItem] fallback（❔）。
 * @param onOpenNewTransaction FAB / 空态「记一笔」回调（弹 [TransactionSheet] 新增态）。
 * @param onOpenEditTransaction 最近交易行点击回调（弹 [TransactionSheet] 编辑态，传被点 tx）。
 * @param onNavigateToTransactions 「查看全部」回调（跳 transactions 页，P-03）。
 * @param onOpenReports 「报表」入口回调（根图导航到 `reports`，§8.6「报表/预算并排入口」）。
 * @param onOpenBudgets 「预算」入口回调（根图导航到 `budgets`，§8.6）。
 * @param onOpenImport 「导入账单」入口回调（根图导航到 `import`，§9.1 TASK-044 第三格）。
 * @param budgetOverBudget 所选月预算是否超支（DEC-012 入口提示）；超支时预算入口叠加 `error` 红点。
 * @param onPreviousMonth 上一月回调（右滑/「‹」箭头，[HomeViewModel.previousMonth]）。
 * @param onNextMonth 下一月回调（左滑/「›」箭头，[HomeViewModel.nextMonth]，上限当前月）。
 */
@Composable
private fun HomeDashboardContent(
    month: YearMonth = YearMonth.now(),
    balance: Long,
    income: Long,
    expense: Long,
    transactions: List<Transaction>,
    categoriesById: Map<String, Category>,
    onOpenNewTransaction: () -> Unit = {},
    onOpenEditTransaction: (Transaction) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    budgetOverBudget: Boolean = false,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonthBalanceCard(
                month = month,
                balance = balance,
                income = income,
                expense = expense,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onOpenMonthTransactions = {
                    val range = monthTransactionsRange(month)
                    BillsFilterBus.post(
                        BillsFilterRequest(
                            categoryId = null,
                            categoryName = null,
                            type = null,
                            dateFrom = range.dateFrom,
                            dateTo = range.dateTo,
                            periodLabel = range.periodLabel,
                        ),
                    )
                    onNavigateToTransactions()
                },
            )
        }
        item {
            HomeQuickEntries(
                overBudget = budgetOverBudget,
                onOpenReports = onOpenReports,
                onOpenBudgets = onOpenBudgets,
                onOpenImport = onOpenImport,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近交易", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNavigateToTransactions) {
                    Text("查看全部")
                }
            }
        }
        if (transactions.isEmpty()) {
            item {
                EmptyState(
                    icon = HomeIcons.Info,
                    title = "暂无交易记录",
                    subtitle = "还没有记录，点右下角 ＋ 记第一笔",
                    actionLabel = "记一笔",
                    onAction = onOpenNewTransaction,
                )
            }
        } else {
            items(
                items = transactions.take(HomeViewModel.MAX_RECENT_TRANSACTIONS),
                key = { it.id },
            ) { tx ->
                val category = categoriesById[tx.categoryId]
                TransactionListItem(
                    tx = tx,
                    categoryName = category?.name,
                    categoryIcon = category?.icon,
                    categoryColor = category?.color,
                    onClick = { onOpenEditTransaction(tx) },
                )
            }
        }
    }
}

/** 结余卡左右滑动切换月份的横向滑动阈值（px 由 [androidx.compose.ui.unit.Density] 换算）。 */
private val MONTH_SWIPE_THRESHOLD_DP = 64.dp

/**
 * 结余卡（所选月份标题 + 左右滑动/箭头切换）：包裹 [NetBalanceCard]，卡片上左右滑动
 * （[detectHorizontalDragGestures] 累计横向位移、超阈值判定方向：左滑=下一月、右滑=上一月），
 * 标题右端叠加「‹/›」箭头按钮辅助切换（当前月时「›」禁用）。箭头与滑动走同一
 * [onPreviousMonth]/[onNextMonth]，由 [HomeViewModel.nextMonth] 上限当前月兜底。
 *
 * 卡片主体（除「‹/›」箭头外）可点击（[Modifier.clickable] 轻点）：点击跳转账单页并按当月
 * 区间筛明细（[onOpenMonthTransactions]，由 [HomeDashboardContent] 组装
 * [BillsFilterBus.post] + 跳「记账」Tab）。clickable 与左右滑动手势不互斥——滑动一旦
 * 越过 touch slop 由 drag 消费并取消 tap，轻点（未拖动）才触发 [onOpenMonthTransactions]；
 * 箭头按钮为上层独立 [IconButton]，自身消费点击不受影响。
 *
 * @param month 所选月份（标题 + 是否已达当前月的判定）。
 * @param balance 所选月结余（分，可负）。
 * @param income 所选月收入（分）。
 * @param expense 所选月支出（分）。
 * @param onPreviousMonth 右滑 /「‹」箭头回调。
 * @param onNextMonth 左滑 /「›」箭头回调。
 * @param onOpenMonthTransactions 结余卡轻点回调（跳转账单当月明细，可空=不可点）。
 */
@Composable
private fun MonthBalanceCard(
    month: YearMonth,
    balance: Long,
    income: Long,
    expense: Long,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthTransactions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLatestMonth = month >= YearMonth.now()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMonthTransactions)
            .pointerInput(month) {
                var dragAccum = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAccum < -MONTH_SWIPE_THRESHOLD_DP.toPx() -> onNextMonth()
                            dragAccum > MONTH_SWIPE_THRESHOLD_DP.toPx() -> onPreviousMonth()
                        }
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragAccum += dragAmount
                }
            },
    ) {
        NetBalanceCard(
            title = monthBalanceTitle(month),
            balanceFen = balance,
            semantic = when {
                balance > 0L -> MoneySemantic.INCOME
                balance < 0L -> MoneySemantic.EXPENSE
                else -> MoneySemantic.NET_ZERO
            },
            rows = listOf(
                NetBalanceRow(label = "收入", fen = income, semantic = MoneySemantic.INCOME),
                NetBalanceRow(label = "支出", fen = expense, semantic = MoneySemantic.EXPENSE),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Text("‹", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNextMonth, enabled = !isLatestMonth) {
                Text(
                    "›",
                    fontSize = 22.sp,
                    color = if (isLatestMonth) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** 所选月份结余卡标题：`2026年8月结余`（TimeFormatter 无中文月份口径，直接 year/monthValue 拼接）。 */
private fun monthBalanceTitle(month: YearMonth): String =
    "${month.year}年${month.monthValue}月结余"

/**
 * 首页结余卡点击跳转账单当月明细所需的筛选区间与周期文案（纯函数，可单测）。
 *
 * dateFrom/dateTo 用 [YearMonth.atDay]（首日）/[YearMonth.atEndOfMonth]（月末）求当月区间，
 * 统一经 [TimeFormatter.formatLocalDate] 输出 `yyyy-MM-dd`；periodLabel 与结余卡标题同口径
 * （`2026年8月`，无「结余」尾缀），供账单查询框/筛选展示。
 *
 * @param month 所选月份（结余卡当前展示月份）。
 */
internal data class MonthTransactionsRange(
    val dateFrom: String,
    val dateTo: String,
    val periodLabel: String,
)

internal fun monthTransactionsRange(month: YearMonth): MonthTransactionsRange =
    MonthTransactionsRange(
        dateFrom = TimeFormatter.formatLocalDate(month.atDay(1)),
        dateTo = TimeFormatter.formatLocalDate(month.atEndOfMonth()),
        periodLabel = "${month.year}年${month.monthValue}月",
    )

/**
 * 首页「报表 / 预算 / 导入账单」并排轻量入口（§8.6 设计约定 + §9.1 TASK-044 增设第三格）：
 * 位于本月结余卡下方，三者 `weight(1f)` 均分。
 *
 * 预算入口在当月超支（[overBudget]）时叠加 `error` 色小圆点（DEC-012「入口轻量提示」，
 * TASK-038 增强项），点击进预算页 `budgets`；报表入口点击进报表页 `reports`；「导入账单」
 * 入口（📥，F-12）点击进根图 `import`。
 *
 * @param overBudget 当月预算是否超支（决定预算入口红点显隐）。
 * @param onOpenReports 「报表」入口回调（根图导航到 `reports`）。
 * @param onOpenBudgets 「预算」入口回调（根图导航到 `budgets`）。
 * @param onOpenImport 「导入账单」入口回调（根图导航到 `import`）。
 */
@Composable
private fun HomeQuickEntries(
    overBudget: Boolean,
    onOpenReports: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeQuickEntry(
            label = "报表",
            icon = "📊",
            onClick = onOpenReports,
            modifier = Modifier.weight(1f),
        )
        HomeQuickEntry(
            label = "预算",
            icon = "🎯",
            onClick = onOpenBudgets,
            overBudget = overBudget,
            modifier = Modifier.weight(1f),
        )
        HomeQuickEntry(
            label = "导入账单",
            icon = "📥",
            onClick = onOpenImport,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 单个首页快捷入口（Surface 轻量卡片）：图标 + 文案 +（可选）超支红点。
 *
 * @param label 入口文案。
 * @param icon 入口图标（emoji 占位，material-icons 缺失同口径）。
 * @param onClick 点击回调。
 * @param overBudget 是否叠加 `error` 红点（仅预算入口使用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeQuickEntry(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overBudget: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (overBudget) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

/**
 * 错误态：一屏提示 + 「重试」按钮（调 [HomeViewModel.refresh]）+ 引导账本管理。
 *
 * 账本未初始化场景（[HomeViewModel.LEDGER_ERROR_MESSAGE]）保留引导文案；其余异常仅展示
 * 重试按钮（UI §2 P-02 Error）。
 *
 * @param message 错误文案。
 * @param onRetry 「重试」回调（[HomeViewModel.refresh]）。
 */
@Composable
private fun HomeErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = HomeIcons.Info,
        title = message,
        subtitle = if (message == HomeViewModel.LEDGER_ERROR_MESSAGE) {
            "请前往账本管理初始化或切换账本"
        } else {
            null
        },
        actionLabel = "重试",
        onAction = onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 首页空态占位图标（material-icons 缺失，TASK-020 偏差）。
 *
 * [EmptyState] 要求 [ImageVector]，且 `material-icons` 库未在依赖集（BOM 无该 artifact）；
 * 故以 [ImageVector.Builder] 手绘「信息圆环 + i」占位（tint 由 [androidx.compose.material3.Icon]
 * 统一覆盖，fill 用黑色无碍）。后续引入 icons 库或统一换字符方案时替换（KDoc 留痕）。
 */
private object HomeIcons {
    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeInfo",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                // 外圆（标准 material circle 路径 M12,2 A10,10 0 1,0 12,22 A10,10 0 1,0 12,2 Z）
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 22f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 2f)
                close()
                // 「i」竖（EvenOdd 挖空）
                moveTo(11f, 10f)
                lineTo(13f, 10f)
                lineTo(13f, 17f)
                lineTo(11f, 17f)
                close()
                // 「i」点（半径 1 圆，EvenOdd 挖空）
                moveTo(12f, 6f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 8f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12f, y1 = 6f)
                close()
            }
        }.build()
    }
}

/** 首页仪表板内容区预览（避开 [com.minibook.ui.theme.MiniBookTheme] 的 Hilt 依赖，仅供 IDE 查看布局）。 */
@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun HomeDashboardPreview() {
    MaterialTheme {
        CompositionLocalProvider(LocalSemanticColors provides lightSemanticColors) {
            HomeDashboardContent(
                balance = 12_345L,
                income = 20_000L,
                expense = 7_655L,
                transactions = listOf(
                    Transaction(
                        id = "t1",
                        ledgerId = "l1",
                        type = TransactionType.EXPENSE,
                        amount = 2500L,
                        categoryId = "c1",
                        description = "午餐",
                        date = "2026-08-18",
                        createdAt = "2026-08-18T12:00:00Z",
                        updatedAt = "2026-08-18T12:00:00Z",
                    ),
                    Transaction(
                        id = "t2",
                        ledgerId = "l1",
                        type = TransactionType.INCOME,
                        amount = 600_000L,
                        categoryId = "c2",
                        description = "工资",
                        date = "2026-08-01",
                        createdAt = "2026-08-01T09:00:00Z",
                        updatedAt = "2026-08-01T09:00:00Z",
                    ),
                ),
                categoriesById = mapOf(
                    "c1" to Category(
                        id = "c1",
                        ledgerId = "l1",
                        name = "餐饮",
                        icon = "🍜",
                        color = "#FF7043",
                        type = CategoryType.EXPENSE,
                        isBuiltIn = true,
                        order = 1,
                        createdAt = "2026-08-01T00:00:00Z",
                        updatedAt = "2026-08-01T00:00:00Z",
                    ),
                    "c2" to Category(
                        id = "c2",
                        ledgerId = "l1",
                        name = "工资",
                        icon = "💰",
                        color = "#66BB6A",
                        type = CategoryType.INCOME,
                        isBuiltIn = true,
                        order = 2,
                        createdAt = "2026-08-01T00:00:00Z",
                        updatedAt = "2026-08-01T00:00:00Z",
                    ),
                ),
            )
        }
    }
}
