package com.minibook.ui.debts

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
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
import com.minibook.ui.components.SwipeToRevealContainer

/**
 * 债务管理页（TASK-025，UI §2 P-04）。
 *
 * 页面结构：顶部 [DebtViewTab]（明细/按对象）+ [NetBalanceCard] 净额卡（卡旁「?」口径说明
 * 按钮 → [ConfirmDialog]）+ 中部三态内容区 + 右下 [PrimaryFAB] 新增入口（跳 [DebtDetailScreen]）。
 *
 * - **明细 Tab**：按 `date` 分组（组头 [DateSectionHeader]），行用 [DebtDetailRow]（C-15，
 *   已内置四态 netContribution 口径与 kind 标签，±金额按净额贡献语义着色）；行点击 →
 *   [DebtDetailScreen] 查看态。
 * - **按对象 Tab**：遍历 `byParty`，行用 [PartyNetRow]（C-14，net>0 对方欠我绿 / net<0
 *   我欠对方红 / net==0 两清灰，行尾「还款」快捷入口）；「还款」→ [DebtDetailScreen] 还款态并按
 *   净额推导方向预填（netFen>0 → [DebtDirection.OWE_ME] 收到还款 / netFen<0 → [DebtDirection.I_OWE]
 *   我还款，PRD F-02 口径）。
 *
 * FAB / 行点击 / 快捷还款已接线：经 [onOpenDebt] 跳根图全屏债务详情页（P-08/P-09 四态合一）。
 * 明细行长按「编辑」经 [onOpenDebtEdit] 跳编辑态；「删除」经 [DebtsViewModel.deleteDebt] 强确认后删除。
 *
 * @param onOpenDebt 打开债务详情页回调（debtId=null 新增 / 非空查看；prefillParty+prefillDirection
 *   非空即进还款态预填）；由 MainScreen 注入根图导航。
 * @param onOpenDebtEdit 打开债务详情页并直接进编辑态（长按菜单「编辑」）；由 MainScreen 注入根图导航。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun DebtsScreen(
    onOpenDebt: (String?, String?, DebtDirection?) -> Unit = { _, _, _ -> },
    onOpenDebtEdit: (String) -> Unit = {},
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabIndex by viewModel.tabIndex.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCriterion by remember { mutableStateOf(false) }
    // 单条删除目标（长按菜单「删除」/ 左滑「删除」→ 强确认后删除）。
    var deleteTarget by remember { mutableStateOf<Debt?>(null) }

    // 单条删除失败等轻提示：展示后立即清除（防重复触发）。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            PrimaryFAB(onClick = { onOpenDebt(null, null, null) })
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
                is DebtsUiState.Error -> ErrorDebts(
                    message = state.message,
                    onRetry = viewModel::refresh,
                )
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
                            linkedDebtIds = state.linkedDebtIds,
                            onItemClick = { debt -> onOpenDebt(debt.id, null, null) },
                            onEditClick = { debt -> onOpenDebtEdit(debt.id) },
                            onDeleteClick = { debt -> deleteTarget = debt },
                        )
                    } else {
                        DebtByPartyList(
                            byParty = state.byParty,
                            onRepay = { party, netFen ->
                                // 按净额推导还款方向（PRD F-02）：
                                //   netFen > 0（对方欠我）→ 收到还款（OWE_ME）；
                                //   netFen < 0（我欠对方）→ 我还款（I_OWE）；
                                //   netFen == 0（两清）→ 默认收到还款预填，仍允许补记。
                                val direction = when {
                                    netFen > 0L -> DebtDirection.OWE_ME
                                    netFen < 0L -> DebtDirection.I_OWE
                                    else -> DebtDirection.OWE_ME
                                }
                                onOpenDebt(null, party, direction)
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

    // 单条删除强确认（长按菜单「删除」/ 左滑「删除」）：确认后调 VM 删除，列表 Flow 自动刷新。
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除这笔债务",
            message = "确定删除「${target.partyName}」的这笔记录吗？删除后不可恢复。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                deleteTarget = null
                viewModel.deleteDebt(target.id)
            },
            onDismiss = { deleteTarget = null },
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
 * @param linkedDebtIds 由账单转入的债务 id 集合（DEC-056，行尾「账单转入」标记）。
 * @param onItemClick 行点击回调（跳详情/编辑态）。
 * @param onEditClick 长按菜单「编辑」回调（跳详情页编辑态）。
 * @param onDeleteClick 长按菜单「删除」/ 左滑「删除」回调（触发单条删除强确认）。
 */
@Composable
private fun DebtDetailsList(
    details: List<Debt>,
    linkedDebtIds: Set<String>,
    onItemClick: (Debt) -> Unit,
    onEditClick: (Debt) -> Unit,
    onDeleteClick: (Debt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(details) {
        details.groupBy { it.date }.toSortedMap(reverseOrder())
    }
    // 单实例展开：当前左滑展开的债务 id（至多一行展开），滑另一行时旧行自动回收。
    var expandedId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // 预留 FAB 浮层空间，末条不被遮挡
    ) {
        grouped.forEach { (date, debts) ->
            item(key = "header_$date") {
                DateSectionHeader(date = date)
            }
            items(items = debts, key = { it.id }) { debt ->
                DebtRow(
                    debt = debt,
                    fromBill = debt.id in linkedDebtIds,
                    onClick = { onItemClick(debt) },
                    onEdit = { onEditClick(debt) },
                    onDelete = { onDeleteClick(debt) },
                    expanded = expandedId == debt.id,
                    onExpand = { expandedId = debt.id },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * 债务明细行（长按居中菜单 + 左滑删除）。
 *
 * 长按与点击分离：onClick 仍打开查看态，onLongClick 弹屏幕居中 [Dialog]。左滑露出「删除」，
 * 点击后只触发外层强确认弹窗（[onDelete]），确认后才真正删除。明细 Tab 无批量模式，
 * 故无需规避批量勾选手势冲突。
 */
@Composable
private fun DebtRow(
    debt: Debt,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    fromBill: Boolean = false,
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
            DebtDetailRow(
                debt = debt,
                onClick = onClick,
                onLongClick = { showMenu = true },
                fromBill = fromBill,
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
                        onClick = { showMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("取消") }
                }
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
 * @param onRepay 行尾「还款」回调（跳 sheet-repay 预填，方向按净额推导，见调用方）。
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

/**
 * 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 「重试」按钮。
 *
 * @param message 错误文案（VM 已折叠）。
 * @param onRetry 「重试」回调（[com.minibook.ui.debts.DebtsViewModel.refresh]）。
 */
@Composable
private fun ErrorDebts(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyWalletIcon,
        title = message,
        subtitle = "加载失败，请重试",
        actionLabel = "重试",
        onAction = onRetry,
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
