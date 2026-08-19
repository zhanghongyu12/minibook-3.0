package com.minibook.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.minibook.ui.main.MainScreen
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
        NavHost(
            navController = rememberNavController(),
            startDestination = if (isLocked) Destination.Lock.route else Destination.Home.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isLocked) {
                // 锁态唯一可达目标：TASK-022 替换为真实 LockScreen。
                composable(Destination.Lock.route) { PlaceholderRoute("锁屏（TASK-022 占位）") }
            } else {
                // 主入口：内含 5 Tab 嵌套导航图（saveState/restoreState 保留各 Tab 状态）。
                composable(Destination.Home.route) { MainScreen() }

                // Phase 2 占位页（P-12~14，TASK-021 注册占位）。
                composable(Destination.Search.route) { PlaceholderRoute("搜索（P-12 占位）") }
                composable(Destination.Reports.route) { PlaceholderRoute("报表（P-13 占位）") }
                composable(Destination.Budgets.route) { PlaceholderRoute("预算（P-14 占位）") }

                // 账本管理（P-11）。
                composable(Destination.LedgerManage.route) { PlaceholderRoute("账本管理（P-11 占位）") }

                // 底部弹层（sheets）家族：可选 `{id}` 查询参数（UUID，§12）。
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
