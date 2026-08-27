package com.minibook.ui.components

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.minibook.domain.model.TransactionType

/**
 * 交易类型筛选标签页（UI §4 C-08）。
 *
 * 三选项：全部 / 支出 / 收入，[TransactionType] 可空表示「全部」。selectedTabIndex 由类型
 * 映射（null→0、EXPENSE→1、INCOME→2），点击反向映射回类型（null=全部）。
 * 选项文案为交易筛选的通用语义词。
 *
 * @param selected 当前选中的交易类型；null 表示「全部」。
 * @param onSelect 选中回调；null 表示选中「全部」。
 */
@Composable
fun TransactionTypeTab(
    selected: TransactionType?,
    onSelect: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = when (selected) {
        null -> 0
        TransactionType.EXPENSE -> 1
        TransactionType.INCOME -> 2
    }
    TabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
        Tab(selected = selectedIndex == 0, onClick = { onSelect(null) }, text = { Text("全部") })
        Tab(selected = selectedIndex == 1, onClick = { onSelect(TransactionType.EXPENSE) }, text = { Text("支出") })
        Tab(selected = selectedIndex == 2, onClick = { onSelect(TransactionType.INCOME) }, text = { Text("收入") })
    }
}

/**
 * 债务视图切换页签（UI §4 C-09）。
 *
 * 两选项：明细 / 按对象（TabRow）。选项文案为债务视图的通用语义词。
 *
 * @param selectedIndex 选中下标（0=明细，1=按对象）。
 */
@Composable
fun DebtViewTab(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
        Tab(selected = selectedIndex == 0, onClick = { onSelect(0) }, text = { Text("明细") })
        Tab(selected = selectedIndex == 1, onClick = { onSelect(1) }, text = { Text("按对象") })
    }
}