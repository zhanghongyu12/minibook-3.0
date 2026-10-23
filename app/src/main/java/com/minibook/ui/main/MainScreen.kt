package com.minibook.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.minibook.domain.model.DebtDirection
import com.minibook.ui.categories.CategoriesScreen
import com.minibook.ui.components.MiniBookIcons
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
 * 2026-10-12（TASK-079，DEC-042）：图标由 emoji 字符换成本项目自带的矢量
 * [com.minibook.ui.components.MiniBookIcons]——emoji 各机型字形不一、无法随选中态着色，
 * 正是用户反馈「样式太老」的直观来源；仓库禁改依赖（无 material-icons），故自带矢量。
 */
private data class MainTab(val route: String, val label: String, val icon: ImageVector)

/** 5 个主页面 Tab：Home / Transactions / Debts / Categories / Settings。 */
private val mainTabs = listOf(
    MainTab(Destination.Home.route, "首页", MiniBookIcons.Home),
    MainTab(Destination.Transactions.route, "记账", MiniBookIcons.Receipt),
    MainTab(Destination.Debts.route, "债务", MiniBookIcons.Wallet),
    MainTab(Destination.Categories.route, "分类", MiniBookIcons.Label),
    MainTab(Destination.Settings.route, "设置", MiniBookIcons.Tune),
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
 * **跨层导航中转**（ledger-manage 注册于根图，嵌套图无法直达）：账本管理入口与设置二级页
 * （`settings-appearance/security/data/sms`）经
 * [com.minibook.ui.home.LocalHomeNavigation] / [com.minibook.ui.settings.LocalSettingsNavigation]
 * 由 [com.minibook.ui.navigation.MiniBookNavHost] 注入，Home / Settings 读取使用，不再经本页参数
 * 穿透（2026-10-10 设置页改版后，账单导入/邮箱凭据入口也随之移入「数据」二级页，本页不再需要
 * `onNavigateToImport` / `onNavigateToMailAccount`）；Home 的「查看全部」用本页自持的嵌套
 * [navController] 跳 [Destination.Transactions]（同 Tab 语义）。内部 Tab 切换复用 [navigateToTab]。
 *
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
        // 本 Scaffold **不消费**系统栏 inset（F-1? 2026-10-13，用户「页面与上部空白太多」）：
        // 它没有 topBar，默认会把状态栏高度加进 innerPadding；而每个 Tab 自己的 Scaffold
        // 又会再加一次系统栏 inset ⇒ 内容顶部被顶下去两倍状态栏高度（模拟器实测约 100dp），
        // 5 个主页面全部如此。这里置零，各页面按自己的 Scaffold 处理一次即可。
        // 底部不需要 offset：NavigationBar 自身消费导航栏 inset，innerPadding.bottom 即其高度。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                // 无阴影 + 色面分层（2025–2026 主流，TASK-079）：底栏改用分层表面色，
                // 选中态由 M3 默认的 tonal pill indicator 承担，不再用整条 secondaryContainer。
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                mainTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(tab.route) },
                        // label 已提供文字，图标不再重复播报（accessibility）。
                        icon = { Icon(imageVector = tab.icon, contentDescription = null) },
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
            // 设置首页 = 分组汇总页（2026-10-10 改版）：账单导入/邮箱凭据入口已移入「数据」二级页，
            // 二级页经 LocalSettingsNavigation 走根图导航，故此处不再需要参数穿透。
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
