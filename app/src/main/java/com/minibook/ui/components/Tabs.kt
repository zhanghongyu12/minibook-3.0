package com.minibook.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.minibook.domain.model.TransactionType

/**
 * 交易类型筛选分段控件（UI §4 C-08）。
 *
 * 三选项：全部 / 支出 / 收入，[TransactionType] 可空表示「全部」。
 * 选项文案为交易筛选的通用语义词，属于纯展示映射；如需个性化可在外层覆盖调用。
 *
 * @param selected 当前选中的交易类型；null 表示「全部」。
 * @param onSelect 选中回调；null 表示选中「全部」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTypeTab(
    selected: TransactionType?,
    onSelect: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = selected == null,
            onClick = { onSelect(null) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            label = { Text("全部") },
        )
        SegmentedButton(
            selected = selected == TransactionType.EXPENSE,
            onClick = { onSelect(TransactionType.EXPENSE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            label = { Text("支出") },
        )
        SegmentedButton(
            selected = selected == TransactionType.INCOME,
            onClick = { onSelect(TransactionType.INCOME) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            label = { Text("收入") },
        )
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
