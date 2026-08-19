package com.minibook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.DebtDirection

/**
 * 还款方向二选一卡片（UI §4 C-24）。
 *
 * 两卡片：收到还款（[DebtDirection.OWE_ME]）/ 我还款（[DebtDirection.I_OWE]），
 * 选中态以 primary 描边 + 弱化容器高亮；[readOnly] 时禁点。
 * 文案为还款域固定展示词（可在外层自定义替换）。
 *
 * @param selected 当前选中方向；null 表示未选。
 * @param readOnly 只读态（如详情查看）。
 */
@Composable
fun RepayDirectionCard(
    selected: DebtDirection?,
    onSelect: (DebtDirection) -> Unit,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DirectionOption(
            label = "收到还款",
            direction = DebtDirection.OWE_ME,
            selected = selected == DebtDirection.OWE_ME,
            readOnly = readOnly,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        DirectionOption(
            label = "我还款",
            direction = DebtDirection.I_OWE,
            selected = selected == DebtDirection.I_OWE,
            readOnly = readOnly,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单方向选项卡片。 */
@Composable
private fun DirectionOption(
    label: String,
    direction: DebtDirection,
    selected: Boolean,
    readOnly: Boolean,
    onSelect: (DebtDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !readOnly) { onSelect(direction) },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
