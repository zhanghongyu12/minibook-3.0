package com.minibook.ui.debts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Debt
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.DateSectionHeader
import com.minibook.ui.components.DebtDetailRow
import com.minibook.ui.components.DebtViewTab
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.NetBalanceCard
import com.minibook.ui.components.NetBalanceRow
import com.minibook.ui.components.PartyNetRow
import com.minibook.ui.components.PrimaryFAB

/**
 * 债务管理页（TASK-025，UI §2 P-04）。
 *
 * 页面结构：顶部 [DebtViewTab]（明细/按对象）+ [NetBalanceCard] 净额卡（卡旁「?」口径说明
 * 按钮 → [ConfirmDialog]）+ 中部三态内容区 + 右下 [PrimaryFAB] 新增入口
 * （TODO：跳 sheet-debt）。
 *
 * - **明细 Tab**：按 `date` 分组（组头 [DateSectionHeader]），行用 [DebtDetailRow]（C-15，
 *   已内置四态 netContribution 口径与 kind 标签，±金额按净额贡献语义着色）。
 * - **按对象 Tab**：遍历 `byParty`，行用 [PartyNetRow]（C-14，net>0 对方欠我绿 / net<0
 *   我欠对方红 / net==0 两清灰，行尾「还款」快捷入口）。
 *
 * 行点击 / FAB / 快捷还款本期暂留 TODO（跳 sheet-repay 预填方向由后续 task 接线）。
 *
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun DebtsScreen(viewModel: DebtsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabIndex by viewModel.tabIndex.collectAsStateWithLifecycle()
    var showCriterion by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            PrimaryFAB(onClick = { /* TODO: TASK-XXX 新增债务 → sheet-debt */ })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DebtViewTab(
                selectedIndex = tabIndex,
                onSelect = viewModel::setTab,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val state = uiState) {
                DebtsUiState.Loading -> {
                    NetBalanceHeader(
                        balanceFen = 0L,
                        semantic = MoneySemantic.NET_ZERO,
                        rows = emptyList(),
                        onShowCriterion = { showCriterion = true },
                    )
                    LoadingState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                }
                DebtsUiState.Empty -> {
                    NetBalanceHeader(
                        balanceFen = 0L,
                        semantic = MoneySemantic.NET_ZERO,
                        rows = emptyList(),
                        onShowCriterion = { showCriterion = true },
                    )
                    EmptyDebts()
                }
                is DebtsUiState.Success -> {
                    NetBalanceHeader(
                        balanceFen = state.totalNet,
                        semantic = netSemantic(state.totalNet),
                        rows = netRows(state.byParty),
                        onShowCriterion = { showCriterion = true },
                    )
                    if (tabIndex == 0) {
                        DebtDetailsList(
                            details = state.details,
                            onItemClick = { /* TODO: TASK-XXX 跳 sheet-debt 编辑/详情态 */ },
                        )
                    } else {
                        DebtByPartyList(
                            byParty = state.byParty,
                            onRepay = { party, netFen ->
                                // TODO: TASK-XXX 跳 sheet-repay 并按净额方向预填：
                                //   netFen > 0（对方欠我）→ 收到还款（repay × owe_me）；
                                //   netFen < 0（我欠对方）→ 我还款（repay × i_owe）；
                                //   netFen == 0（两清）→ 仅当仍需要时允许补记。
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCriterion) {
        ConfirmDialog(
            title = "净额口径说明",
            message = "借出/我还款=资产增加(+)；借入/收到还款=负债增加(−)",
            confirmLabel = "知道了",
            onConfirm = { showCriterion = false },
            onDismiss = { showCriterion = false },
        )
    }
}

/**
 * 顶部净额区：净额卡 + 卡旁「?」口径说明按钮。
 *
 * [NetBalanceCard]（C-12）以大字展示净额（[MoneyText] 内部格式化、按 [semantic] 着色），
 * [rows] 展示正/负分项合计；「?」按钮（material-icons 缺失，TASK-020 偏差，用 `Text("?")`
 * 占位）触发口径说明 dialog。
 *
 * @param balanceFen 净额合计（分）。
 * @param semantic 净额语义色（正→INCOME / 负→EXPENSE / 零→NET_ZERO）。
 * @param rows 正/负分项行（空列表不渲染明细区）。
 * @param onShowCriterion 「?」点击回调（外层弹 [ConfirmDialog]）。
 */
@Composable
private fun NetBalanceHeader(
    balanceFen: Long,
    semantic: MoneySemantic,
    rows: List<NetBalanceRow>,
    onShowCriterion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetBalanceCard(
            title = "债务净额",
            balanceFen = balanceFen,
            semantic = semantic,
            rows = rows,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onShowCriterion,
            modifier = Modifier.semantics { contentDescription = "净额口径说明" },
        ) {
            Text("?")
        }
    }
}

/**
 * 明细列表：按 `date` 倒序分组渲染。
 *
 * 分组在 UI 层用 `remember(details)` 完成（VM 只提供扁平列表）；组头 key 加 `header_`
 * 前缀与行 key（`id`）隔离，`animateItem` 提供条目增删/移动的位移动画。
 *
 * @param details 扁平列表（DAO 已按 createdAt DESC 排序，故组内天然倒序）。
 * @param onItemClick 行点击回调（跳编辑/详情态）。
 */
@Composable
private fun DebtDetailsList(
    details: List<Debt>,
    onItemClick: (Debt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(details) {
        details.groupBy { it.date }.toSortedMap(reverseOrder())
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // 预留 FAB 浮层空间，末条不被遮挡
    ) {
        grouped.forEach { (date, debts) ->
            item(key = "header_$date") {
                DateSectionHeader(date = date)
            }
            items(items = debts, key = { it.id }) { debt ->
                DebtDetailRow(
                    debt = debt,
                    onClick = { onItemClick(debt) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * 按对象列表：遍历 `byParty` 渲染 [PartyNetRow]。
 *
 * 行内已内置三态口径与红/绿/灰语义色（net>0 对方欠我 INCOME 绿 / net<0 我欠对方
 * EXPENSE 红绝对值 / net==0 两清 NET_ZERO 灰）。排序按净额降序（对方欠我优先），
 * 语义无强依赖，仅展示顺序。
 *
 * @param byParty 对方姓名 → 净额（分）映射。
 * @param onRepay 行尾「还款」回调（跳 sheet-repay 预填，方向推导见调用方 TODO）。
 */
@Composable
private fun DebtByPartyList(
    byParty: Map<String, Long>,
    onRepay: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(byParty) {
        byParty.entries.sortedByDescending { it.value }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // 预留 FAB 浮层空间，末条不被遮挡
    ) {
        items(items = entries, key = { it.key }) { (party, netFen) ->
            PartyNetRow(
                partyName = party,
                netFen = netFen,
                onRepay = { onRepay(party, netFen) },
            )
        }
    }
}

/** 空态：暂无任何债务记录。 */
@Composable
private fun EmptyDebts(modifier: Modifier = Modifier) {
    EmptyState(
        icon = EmptyWalletIcon,
        title = "暂无债务记录",
        subtitle = "点击右下角「＋」记一笔",
        modifier = modifier.fillMaxSize(),
    )
}

/** 净额合计 → 语义色（正=对方欠我绿 / 负=我欠对方红 / 零=两清灰）。 */
private fun netSemantic(totalNet: Long): MoneySemantic = when {
    totalNet > 0L -> MoneySemantic.INCOME
    totalNet < 0L -> MoneySemantic.EXPENSE
    else -> MoneySemantic.NET_ZERO
}

/**
 * 净额卡分项行：正/负合计。
 *
 * - 「对方欠我」= 全部正值之和（INCOME 绿）；
 * - 「我欠对方」= 全部负值绝对值和（EXPENSE 红）。
 * 两清（0）对象不计入任一分项；两行为空时由 [NetBalanceCard] 不渲染明细区。
 */
private fun netRows(byParty: Map<String, Long>): List<NetBalanceRow> {
    val positive = byParty.values.filter { it > 0L }.sum()
    val negativeAbs = byParty.values.filter { it < 0L }.sumOf { -it }
    return buildList {
        if (positive > 0L) add(NetBalanceRow(label = "对方欠我", fen = positive, semantic = MoneySemantic.INCOME))
        if (negativeAbs > 0L) add(NetBalanceRow(label = "我欠对方", fen = negativeAbs, semantic = MoneySemantic.EXPENSE))
    }
}

/**
 * 空态图标：手写「钱包」ImageVector。
 *
 * TASK-020 偏差：工程未引入 material-icons 库（MainScreen 同款注记），[EmptyState] 又要求
 * 注入 [ImageVector]，故用 [addPathNodes] 解析 Material「account_balance_wallet」path 数据
 * 本地构造，不新增依赖、不改 toml/build.gradle。图标为纯填充矢量，tint 由 [EmptyState] 内
 * `Icon(tint=...)` 统一上色，此处 fill 色仅占位（Black，不参与渲染）。
 */
private val EmptyWalletIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyDebts",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1" +
                "h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67" +
                "-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
