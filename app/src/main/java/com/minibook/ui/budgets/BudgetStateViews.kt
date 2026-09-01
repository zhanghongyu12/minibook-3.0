package com.minibook.ui.budgets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.minibook.ui.components.EmptyState

/** 空态：未设任何预算 → 「设置月度总预算」引导（含设置动作）。 */
@Composable
internal fun EmptyBudgets(
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyBudgetIcon,
        title = "设置月度总预算，追踪本月支出",
        subtitle = "从「月度总预算」或「分类预算」开始设置",
        actionLabel = "设置总预算",
        onAction = onSetup,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 「重试」按钮。
 *
 * @param message 错误文案（[com.minibook.ui.budgets.BudgetsViewModel] 已折叠）。
 * @param onRetry 「重试」回调（[com.minibook.ui.budgets.BudgetsViewModel.refresh]）。
 */
@Composable
internal fun ErrorBudgets(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyBudgetIcon,
        title = message,
        subtitle = "加载失败，请重试",
        actionLabel = "重试",
        onAction = onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 空态图标：手写「钱包」ImageVector。
 *
 * 工程未引入 material-icons 库，[EmptyState] 又要求注入 [ImageVector]，故用 [addPathNodes]
 * 解析 Material「account_balance_wallet」path 数据本地构造，不新增依赖、不改
 * toml/build.gradle（TASK-020 偏差同口径）。图标为纯填充矢量，tint 由 [EmptyState] 内
 * `Icon(tint=...)` 统一上色，此处 fill 色仅占位（Black，不参与渲染）。
 */
private val EmptyBudgetIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyBudgets",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9" +
                "c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5" +
                "s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}