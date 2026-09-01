package com.minibook.ui.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.Category
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.OptionalDateField
import com.minibook.ui.components.TransactionTypeTab
import com.minibook.ui.search.SearchTab

/** 条件组切换：「交易 | 债务」SegmentedButton（UI §8.1）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchTabBar(tab: SearchTab, onSelect: (SearchTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = tab == SearchTab.TRANSACTION,
            onClick = { onSelect(SearchTab.TRANSACTION) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("交易") },
        )
        SegmentedButton(
            selected = tab == SearchTab.DEBT,
            onClick = { onSelect(SearchTab.DEBT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("债务") },
        )
    }
}

/** 交易组条件（UI §8.1）：备注关键词 / 金额区间 / 分类 / 日期区间 / 类型。 */
@Composable
internal fun TransactionConditions(
    remark: String,
    onRemarkChange: (String) -> Unit,
    amountMinFen: Long?,
    onAmountMinFenChange: (Long?) -> Unit,
    amountMaxFen: Long?,
    onAmountMaxFenChange: (Long?) -> Unit,
    amountRangeInvalid: Boolean,
    rangeErrorMessage: String?,
    selectedCategory: Category?,
    onCategoryClick: () -> Unit,
    dateFrom: String?,
    onDateFromChange: (String?) -> Unit,
    dateTo: String?,
    onDateToChange: (String?) -> Unit,
    type: TransactionType?,
    onTypeChange: (TransactionType?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = remark,
            onValueChange = onRemarkChange,
            label = { Text("备注包含…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // 金额区间：MinFenField + ~ + MaxFenField（C-01 ×2，元↔分）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyInputField(
                fen = amountMinFen,
                onFenChange = onAmountMinFenChange,
                label = "最小金额",
                isError = amountRangeInvalid,
                errorMessage = rangeErrorMessage,
                modifier = Modifier.weight(1f),
            )
            Text("~", modifier = Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyInputField(
                fen = amountMaxFen,
                onFenChange = onAmountMaxFenChange,
                label = "最大金额",
                isError = amountRangeInvalid,
                errorMessage = rangeErrorMessage,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        CategoryFilterChip(category = selectedCategory, onClick = onCategoryClick)

        Spacer(Modifier.height(8.dp))

        // 日期区间：起 / 止（C-10 ×2，可清空）
        Row(modifier = Modifier.fillMaxWidth()) {
            OptionalDateField(
                label = "起",
                date = dateFrom,
                onDateChange = onDateFromChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OptionalDateField(
                label = "止",
                date = dateTo,
                onDateChange = onDateToChange,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        TransactionTypeTab(
            selected = type,
            onSelect = onTypeChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))
    }
}

/** 债务组条件（UI §8.1）：姓名关键词 / 方向（借出·借入）。 */
@Composable
internal fun DebtConditions(
    partyName: String,
    onPartyNameChange: (String) -> Unit,
    direction: DebtDirection?,
    onDirectionChange: (DebtDirection?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = partyName,
            onValueChange = onPartyNameChange,
            label = { Text("对方姓名包含…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // 方向：二选一 Card「借出 | 借入」（kind 口径 → DebtDirection，null=全部）
        DebtDirectionSelector(direction = direction, onSelect = onDirectionChange)

        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 分类筛选 Chip：未选 → 「全部分类」（surfaceVariant）；已选 → 分类 icon+名（primaryContainer）。
 * 点击弹 [com.minibook.ui.components.CategoryPickerSheet]（C-02，type 随下方类型）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryFilterChip(category: Category?, onClick: () -> Unit) {
    val selected = category != null
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(category?.icon ?: "🗂", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                category?.name ?: "全部分类",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 债务方向选择：二选一「借出 | 借入」Card（kind 口径）。
 *
 * 映射 [DebtDirection]：借出=OWE_ME / 借入=I_OWE；未选中=不过滤（再点一次取消）。呈 Card（Surface + 边框）
 * 以对齐 UI §8.1「二选一 Card」。标签语义词属纯展示映射。
 */
@Composable
internal fun DebtDirectionSelector(direction: DebtDirection?, onSelect: (DebtDirection?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        DirectionOption(
            label = "借出",
            selected = direction == DebtDirection.OWE_ME,
            onClick = { onSelect(if (direction == DebtDirection.OWE_ME) null else DebtDirection.OWE_ME) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DirectionOption(
            label = "借入",
            selected = direction == DebtDirection.I_OWE,
            onClick = { onSelect(if (direction == DebtDirection.I_OWE) null else DebtDirection.I_OWE) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 方向单选项 Card（Surface + 边框 + 选中主色底）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Text(if (selected) "✓" else "", color = MaterialTheme.colorScheme.primary)
        }
    }
}