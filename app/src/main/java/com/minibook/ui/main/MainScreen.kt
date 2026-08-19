package com.minibook.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.minibook.ui.navigation.Destination
import com.minibook.ui.navigation.PlaceholderRoute

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
 * 各 Tab 状态，§12）。页面内容本期为 [PlaceholderRoute] 占位（传不同 label），TASK-022~030
 * 由各自页面 composable 替换。
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                mainTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
            mainTabs.forEach { tab ->
                composable(tab.route) { PlaceholderRoute(tab.label) }
            }
        }
    }
}
