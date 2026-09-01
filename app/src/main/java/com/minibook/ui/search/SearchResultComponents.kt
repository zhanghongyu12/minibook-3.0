package com.minibook.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.Category
import com.minibook.domain.model.Debt
import com.minibook.domain.model.Transaction
import com.minibook.ui.components.DebtDetailRow
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.TransactionListItem

/** 结果区：交易 / 债务两组，组内时间倒序，行只读点击。 */
@Composable
internal fun SearchResults(
    transactions: List<Transaction>,
    debts: List<Debt>,
    categoriesById: Map<String, Category>,
    onTxClick: (Transaction) -> Unit,
    onDebtClick: (Debt) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (transactions.isNotEmpty()) {
            item(key = "group_transaction") { GroupHeader(label = "交易", count = transactions.size) }
            items(items = transactions, key = { it.id }) { tx ->
                val category = categoriesById[tx.categoryId]
                TransactionListItem(
                    tx = tx,
                    categoryName = category?.name,
                    categoryIcon = category?.icon,
                    categoryColor = category?.color,
                    onClick = { onTxClick(tx) },
                )
            }
        }
        if (debts.isNotEmpty()) {
            item(key = "group_debt") { GroupHeader(label = "债务", count = debts.size) }
            items(items = debts, key = { it.id }) { debt ->
                DebtDetailRow(
                    debt = debt,
                    onClick = { onDebtClick(debt) },
                )
            }
        }
    }
}

/** 结果分组头（UI §8.1「分组头『交易 (N)』」）。 */
@Composable
private fun GroupHeader(label: String, count: Int) {
    Text(
        text = "$label ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 空态：无输入 → 引导输入；有条件但 0 命中 → 「没有匹配的记录」+ 清除条件。 */
@Composable
internal fun EmptySearch(hasActiveFilter: Boolean, onClear: () -> Unit) {
    if (hasActiveFilter) {
        EmptyState(
            icon = SearchIcon,
            title = "没有匹配的记录",
            subtitle = "换个筛选条件试试",
            actionLabel = "清除条件",
            onAction = onClear,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        EmptyState(
            icon = SearchIcon,
            title = "输入关键词或设置筛选条件开始搜索",
            subtitle = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 错误态：min>max（无重试）或账本/加载异常（带重试）。 */
@Composable
internal fun ErrorSearch(message: String, onRetry: (() -> Unit)?) {
    if (onRetry != null) {
        EmptyState(
            icon = SearchIcon,
            title = message,
            subtitle = "加载失败，请重试",
            actionLabel = "重试",
            onAction = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        EmptyState(
            icon = SearchIcon,
            title = message,
            subtitle = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 空态/错误态图标：手写「放大镜」ImageVector（material-icons 缺失，TASK-020 偏差同口径）。 */
private val SearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SearchIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 " +
                "5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z" +
                "M9.5 14C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5s4.5 2.01 4.5 4.5S11.99 14 9.5 14z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}