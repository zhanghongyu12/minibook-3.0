package com.minibook.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LedgerSelector
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.NetBalanceCard
import com.minibook.ui.components.NetBalanceRow
import com.minibook.ui.components.PrimaryFAB
import com.minibook.ui.components.TransactionListItem
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.ui.theme.lightSemanticColors

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
 * **分类联表 TODO**：首页只有 [Transaction]（无分类名/图标/色），本期简化传 null 分类信息，
 * 由 [TransactionListItem] fallback 占位（❔）；后续经 `ObserveCategoriesUseCase` 联表
 * id→[Category] 映射优化（KDoc 留痕）。
 *
 * **导航 TODO**：LedgerSelector → 账本管理（P-11）；「查看全部」→ transactions（P-03）；
 * FAB → sheet-transaction（P-07）。本期由编排者统一接线，本页留空 onClick。
 *
 * @param viewModel 首页 VM；默认经 [hiltViewModel] 解析。
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { HomeTopBar(uiState) },
        floatingActionButton = {
            PrimaryFAB(onClick = { /* TODO(P-07): 跳转 sheet-transaction 记一笔 */ })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                HomeUiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(16.dp))
                is HomeUiState.Error -> HomeErrorContent(state.message)
                is HomeUiState.Empty -> HomeDashboardContent(
                    balance = 0L,
                    income = 0L,
                    expense = 0L,
                    transactions = emptyList(),
                )
                is HomeUiState.Success -> HomeDashboardContent(
                    balance = state.balance,
                    income = state.income,
                    expense = state.expense,
                    transactions = state.recentTransactions,
                )
            }
        }
    }
}

/** 顶部栏：左「首页」标题 + 右 [LedgerSelector]（UI §2 P-02 顶栏；状态栏 inset 自处理）。 */
@Composable
private fun HomeTopBar(uiState: HomeUiState, modifier: Modifier = Modifier) {
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
        LedgerSelector(
            ledger = currentLedger,
            onClick = { /* TODO(P-11): 跳转账本管理 */ },
        )
    }
}

/**
 * 首页仪表板主体内容（[HomeUiState.Empty] / [HomeUiState.Success] 共用）：
 * 结余卡（[NetBalanceCard]）+「最近交易」区（列表或空态）。
 *
 * @param balance 本月结余（分，可负）；负值经 [NetBalanceCard]/[MoneyText] 红色展示。
 * @param income 本月收入（分）。
 * @param expense 本月支出（分）。
 * @param transactions 最近交易（调用方已截断前 10，A-3；此处再 take 兜底）。
 */
@Composable
private fun HomeDashboardContent(
    balance: Long,
    income: Long,
    expense: Long,
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NetBalanceCard(
                title = "本月结余",
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
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近交易", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { /* TODO(P-03): 跳转 transactions 页 */ }) {
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
                    onAction = { /* TODO(P-07): 跳转 sheet-transaction */ },
                )
            }
        } else {
            items(
                items = transactions.take(HomeViewModel.MAX_RECENT_TRANSACTIONS),
                key = { it.id },
            ) { tx ->
                TransactionListItem(
                    tx = tx,
                    categoryName = null,
                    categoryIcon = null,
                    categoryColor = null,
                    onClick = { /* TODO: 交易详情/编辑（P-07） */ },
                )
            }
        }
    }
}

/** 错误态：一屏提示 + 引导账本管理（本期无跳转，仅文案；UI §2 P-02 Error）。 */
@Composable
private fun HomeErrorContent(message: String, modifier: Modifier = Modifier) {
    EmptyState(
        icon = HomeIcons.Info,
        title = message,
        subtitle = "请前往账本管理初始化或切换账本",
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
            )
        }
    }
}
