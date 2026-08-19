package com.minibook.ui.reserved

import androidx.compose.runtime.Composable
import com.minibook.ui.components.PlaceholderPage

/**
 * Phase 2 占位页：搜索（UI §2 P-12，F-06）。
 *
 * 复用 C-25 [PlaceholderPage] 骨架：顶部返回 + 标题，中部「禁用示意」+ 文案。
 * 搜索功能将在后续版本提供；route `search` 已在 TASK-021 Destinations 注册，
 * 但默认不可达（底部栏不展示，NavHost 接线由编排者统一处理）。
 *
 * @param onBack 返回回调。
 */
@Composable
fun SearchPlaceholder(onBack: () -> Unit) {
    PlaceholderPage(
        title = "搜索",
        message = "搜索功能将在后续版本提供",
        onBack = onBack,
    )
}

/**
 * Phase 2 占位页：报表统计（UI §2 P-13，F-07）。
 *
 * 复用 C-25 [PlaceholderPage] 骨架：顶部返回 + 标题，中部「禁用示意」+ 文案。
 * 报表功能将在后续版本提供；route `reports` 已在 TASK-021 Destinations 注册，
 * 但默认不可达（底部栏不展示，NavHost 接线由编排者统一处理）。
 *
 * @param onBack 返回回调。
 */
@Composable
fun ReportsPlaceholder(onBack: () -> Unit) {
    PlaceholderPage(
        title = "报表统计",
        message = "报表功能将在后续版本提供",
        onBack = onBack,
    )
}

/**
 * Phase 2 占位页：预算管理（UI §2 P-14，F-08）。
 *
 * 复用 C-25 [PlaceholderPage] 骨架：顶部返回 + 标题，中部「禁用示意」+ 文案。
 * 预算功能将在后续版本提供；route `budgets` 已在 TASK-021 Destinations 注册，
 * 但默认不可达（底部栏不展示，NavHost 接线由编排者统一处理）。
 *
 * @param onBack 返回回调。
 */
@Composable
fun BudgetsPlaceholder(onBack: () -> Unit) {
    PlaceholderPage(
        title = "预算管理",
        message = "预算功能将在后续版本提供",
        onBack = onBack,
    )
}
