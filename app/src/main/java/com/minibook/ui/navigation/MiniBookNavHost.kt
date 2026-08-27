package com.minibook.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.minibook.data.local.security.AppLockController
import com.minibook.ui.budgets.BudgetsScreen
import com.minibook.ui.home.HomeNavigation
import com.minibook.ui.home.LocalHomeNavigation
import com.minibook.ui.billimport.ImportScreen
import com.minibook.ui.lock.LockScreen
import com.minibook.ui.main.MainScreen
import com.minibook.ui.reports.ReportsScreen
import com.minibook.ui.search.SearchScreen
import com.minibook.ui.settings.LedgerManageScreen
import com.minibook.ui.transactions.TransactionDetailScreen
import com.minibook.ui.transactions.TransactionsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * 根级导航门控 ViewModel（TASK-021）。
 *
 * 仅注入 [AppLockController] 并透传其 [AppLockController.isLocked]，供 [MiniBookNavHost]
 * 顶层门控。不承载其他业务状态（页面级状态由各自 TASK 的 ViewModel 负责）。
 */
@HiltViewModel
class NavRootViewModel @Inject constructor(
    appLockController: AppLockController,
) : ViewModel() {

    /** 锁状态：true=锁定（只可达 [Destination.Lock]）；false=解锁（主入口 + 全路由）。 */
    val isLocked: StateFlow<Boolean> = appLockController.isLocked
}

/** sheets 家族共享的 `{id}` 查询参数（UUID，§12）：可空、默认 null，未传时匹配 base route。 */
private val sheetIdArguments = listOf(
    navArgument("id") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    },
)

/**
 * 根级导航宿主（ARCHITECTURE §12、TASK-018 壳 → TASK-021 填充）。
 *
 * 顶层 `isLocked` 门控（§9）：锁定态 NavHost 只注册 [Destination.Lock]（唯一可达，真解密门，
 * 可达性即安全性）；解锁态注册主入口 [Destination.Home]（[MainScreen]，内嵌 5 Tab 嵌套图）
 * + sheets 家族 / Phase 2 占位页 / 账本管理。`key(isLocked)` 使锁态切换时重建整个导航图
 * （清空返回栈，杜绝越权可达）。
 *
 * @param viewModel 根级导航门控 VM；默认经 [hiltViewModel] 解析（Activity 级 owner）。
 */
@Composable
fun MiniBookNavHost(viewModel: NavRootViewModel = hiltViewModel()) {
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()

    key(isLocked) {
        val rootController = rememberNavController()
        NavHost(
            navController = rootController,
            startDestination = if (isLocked) Destination.Lock.route else Destination.Home.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isLocked) {
                // 锁态唯一可达目标：真实解锁门（TASK-022），DEC-003/DEC-002 路径见 LockScreen。
                composable(Destination.Lock.route) { LockScreen() }
            } else {
                // 主入口：内含 5 Tab 嵌套导航图（saveState/restoreState 保留各 Tab 状态）。
                // 中转 ledger-manage 根图跳转（MainScreen 内嵌图无法直达根图目的地）。
                // 首页跨层根导航桥（search/reports/budgets）：Home 位于嵌套图，直达根图
                // 目的地需本根 Controller navigate 中转，经 LocalHomeNavigation 注入给 HomeScreen。
                composable(Destination.Home.route) {
                    val homeNavigation = remember(rootController) {
                        object : HomeNavigation {
                            override fun openSearch() {
                                rootController.navigate(Destination.Search.route)
                            }
                            override fun openReports() {
                                rootController.navigate(Destination.Reports.route)
                            }
                            override fun openBudgets() {
                                rootController.navigate(Destination.Budgets.route)
                            }
                            override fun openImport() {
                                rootController.navigate(Destination.Import.route)
                            }
                        }
                    }
                    CompositionLocalProvider(LocalHomeNavigation provides homeNavigation) {
                        MainScreen(
                            onNavigateToLedgerManage = {
                                rootController.navigate(Destination.LedgerManage.route)
                            },
                            onNavigateToImport = {
                                rootController.navigate(Destination.Import.route)
                            },
                            onOpenTransaction = { txId ->
                                rootController.navigate(Destination.TransactionDetail.createRoute(txId))
                            },
                        )
                    }
                }

                // Phase 2 真实页（P-12~14，TASK-033/036/038 落地）：顶栏返回 → 根图 popBackStack。
                composable(Destination.Search.route) {
                    SearchScreen(
                        onBack = { rootController.popBackStack() },
                        onOpenTransactionDetail = { txId ->
                            rootController.navigate(Destination.TransactionDetail.createRoute(txId))
                        },
                    )
                }
                composable(Destination.Reports.route) {
                    ReportsScreen(
                        onBack = { rootController.popBackStack() },
                        onOpenBills = { rootController.navigate(Destination.Transactions.route) },
                    )
                }
                composable(Destination.Budgets.route) {
                    BudgetsScreen(
                        onBack = { rootController.popBackStack() },
                        onOpenBills = { rootController.navigate(Destination.Transactions.route) },
                    )
                }
                // 根图账单明细页：统计页（报表/预算）跳转账单筛选明细复用，返回一次回到来源统计页
                // （跳转用 navigate 前压栈而非 popBackStack 弹出来源页，故返回栈保留来源页）。
                composable(Destination.Transactions.route) {
                    TransactionsScreen(
                        onBack = { rootController.popBackStack() },
                        onOpenTransaction = { txId ->
                            rootController.navigate(Destination.TransactionDetail.createRoute(txId))
                        },
                    )
                }
                // 交易详情全屏页（新增/查看/编辑三合一，P-07）：`?id={id}` 可选（null=新增）。
                composable(
                    Destination.TransactionDetail.route,
                    arguments = sheetIdArguments,
                ) { entry ->
                    TransactionDetailScreen(
                        txId = entry.arguments?.getString("id"),
                        onBack = { rootController.popBackStack() },
                    )
                }
                composable(Destination.Import.route) {
                    ImportScreen(onBack = { rootController.popBackStack() })
                }

                // 账本管理（P-11，TASK-029 真实页面）：顶栏返回 → 根图 popBackStack。
                composable(Destination.LedgerManage.route) {
                    LedgerManageScreen(onBack = { rootController.popBackStack() })
                }

                // 底部弹层（sheets）家族：可选 `{id}` 查询参数（UUID，§12）。
                // Sheet composable 接收领域对象（编辑态）而非 route id，故各 Sheet 的实际
                // 弹起由宿主页面内部状态驱动（FAB/行点击 → 页内 ModalBottomSheet），不走
                // NavHost route 跳转；此处仅注册占位以保导航图完整，待页面内联 Sheet 落地。
                sheetRoute(Destination.SheetTransaction, "记一笔")
                sheetRoute(Destination.SheetDebt, "债务明细")
                sheetRoute(Destination.SheetRepay, "还款")
                sheetRoute(Destination.SheetCategoryPicker, "选择分类")
                sheetRoute(Destination.SheetCategoryEditor, "编辑分类")
                sheetRoute(Destination.SheetLedger, "账本")
            }
        }
    }
}

/**
 * sheets 占位注册（TASK-021）。
 *
 * 统一注册带可选 `{id}` 查询参数的占位目的地；`id` 为空时省略查询参数。
 *
 * @param destination sheets 路由（[SheetRoute] 子类）。
 * @param label 占位文案前缀（id 非空时追加 `（id=…）`）。
 */
private fun NavGraphBuilder.sheetRoute(destination: SheetRoute, label: String) {
    composable(destination.route, arguments = sheetIdArguments) { entry ->
        val id = entry.arguments?.getString("id")
        PlaceholderRoute(label + (id?.let { "（id=$it）" } ?: ""))
    }
}
