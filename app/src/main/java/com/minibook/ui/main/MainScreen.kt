package com.minibook.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.minibook.domain.model.DebtDirection
import com.minibook.ui.categories.CategoriesScreen
import com.minibook.ui.debts.DebtsScreen
import com.minibook.ui.home.HomeScreen
import com.minibook.ui.navigation.BillsFilterBus
import com.minibook.ui.navigation.Destination
import com.minibook.ui.settings.SettingsScreen
import com.minibook.ui.transactions.TransactionsScreen
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * 底部导航 Tab 定义（TASK-021，UI §4 C-07 简化版）。
 *
 * material-icons 缺失（TASK-020 偏差），icon 槽用 emoji 字符占位，不依赖 ImageVector；
 * 与 components/Navigation.kt 的 [com.minibook.ui.components.BottomNavItem]（要求
 * ImageVector）区分，避免重复定义。
 */
private data class MainTab(val route: String, val label: String, val emoji: String)

/** 5 个主页面 Tab：Home / Transactions / Debts / Categories / Settings。 */
private val mainTabs = listOf(
    MainTab(Destination.Home.route, "首页", "🏠"),
    MainTab(Destination.Transactions.route, "记账", "📋"),
    MainTab(Destination.Debts.route, "债务", "💰"),
    MainTab(Destination.Categories.route, "分类", "🏷️"),
    MainTab(Destination.Settings.route, "设置", "⚙️"),
)

/**
 * 主入口页壳（TASK-021，ARCHITECTURE §12）。
 *
 * 底部 [NavigationBar]（5 Tab）+ 内嵌嵌套导航图：自持 `rememberNavController()`，以
 * [Destination.Home] 为 startDestination，5 Tab 为子目的地（嵌套导航，与根级锁门控分层）。
 * Tab 切换用 `launchSingleTop + saveState + restoreState`（popUpTo startDestination 保留
 * 各 Tab 状态，§12）。各 Tab 内容已接线为真实页面 composable（TASK-023~026）：
 * Home / Transactions / Debts / Categories / Settings。
 *
 * **跨层导航中转**（ledger-manage 注册于根图，嵌套图无法直达）：本页接收外层
 * [onNavigateToLedgerManage] 回调（由 [MiniBookNavHost] 传入根图 navigate），并分发给
 * Home / Settings 两个入口；Home 的「查看全部」用本页自持的嵌套 [navController] 跳
 * [Destination.Transactions]（同 Tab 语义）。内部 Tab 切换复用 [navigateToTab]。
 *
 * @param onNavigateToLedgerManage 账本管理导航回调（根图跳转，MiniBookNavHost 接线传入）。
 * @param onNavigateToImport 账单导入导航回调（根图跳转到 `import`，MiniBookNavHost 接线传入）。
 * @param onOpenTransaction 交易详情页导航回调（根图跳转到 `transaction-detail`，null=新增；
 *   MiniBookNavHost 接线传入，分发到 Home / Transactions 两个 Tab 的入口）。
 * @param onOpenTransactionEdit 交易详情页「编辑态」导航回调（根图跳转到 `transaction-detail?edit=1`，
 *   列表长按菜单「编辑」用）；分发到 Transactions Tab。
 * @param onOpenDebt 债务详情页导航回调（根图跳转到 `debt-detail`；id=null 新增/还款预填时传
 *   party/direction）；MiniBookNavHost 接线传入，分发到 Debts Tab 入口。
 * @param onOpenDebtEdit 债务详情页「编辑态」导航回调（根图跳转到 `debt-detail?edit=1`，列表
 *   长按菜单「编辑」用）；分发到 Debts Tab。
 */
@Composable
fun MainScreen(
    onNavigateToLedgerManage: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onOpenTransaction: (String?) -> Unit = {},
    onOpenTransactionEdit: (String) -> Unit = {},
    onOpenDebt: (String?, String?, DebtDirection?) -> Unit = { _, _, _ -> },
    onOpenDebtEdit: (String) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 嵌套图内 Tab 跳转语义：launchSingleTop + saveState/restoreState（各 Tab 状态保留）。
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 报表分类占比点击 → 跳「记账」Tab（账单 VM 消费总线后 clear，此处不 clear）。
    LaunchedEffect(Unit) {
        BillsFilterBus.request.filterNotNull().first()
        navigateToTab(Destination.Transactions.route)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                mainTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(tab.route) },
                        icon = { Text(tab.emoji) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onNavigateToTransactions = { navigateToTab(Destination.Transactions.route) },
                    onNavigateToLedgerManage = onNavigateToLedgerManage,
                    onOpenTransaction = onOpenTransaction,
                )
            }
            composable(Destination.Transactions.route) {
                TransactionsScreen(
                    onOpenTransaction = onOpenTransaction,
                    onOpenTransactionEdit = onOpenTransactionEdit,
                )
            }
            composable(Destination.Debts.route) {
                DebtsScreen(
                    onOpenDebt = onOpenDebt,
                    onOpenDebtEdit = onOpenDebtEdit,
                )
            }
            composable(Destination.Categories.route) { CategoriesScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onNavigateToLedgerManage = onNavigateToLedgerManage,
                    onNavigateToImport = onNavigateToImport,
                )
            }
        }
    }
}
