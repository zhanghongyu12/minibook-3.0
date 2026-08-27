package com.minibook.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.util.TimeFormatter
import kotlin.math.absoluteValue

/**
 * 交易列表行（UI §4 C-13，§9.3.1 F-12 增强）。
 *
 * 分类 emoji 圆底 + 分类名（尾部 [SourceBadge]，仅 `source ≠ MANUAL`）+ 0~2 行副文本
 * （先 `counterparty` 交易对方、后 `description`，各自空则不渲染；§9.3.1）+ ±金额
 * （语义色，经 [MoneyText]）+ 日期。分类展示字段（名称/图标/颜色）由调用方从分类表联表后注入；
 * 金额符号由 `tx.type` 决定语义色（收入绿/支出红）。金额/日期/语义色右列与分类圆底色规则不变。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionListItem(
    tx: Transaction,
    categoryName: String?,
    categoryIcon: String?,
    categoryColor: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val fallback = MaterialTheme.colorScheme.outline
    val iconColor = remember(categoryColor) { parseCategoryHex(categoryColor, fallback) }
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoryIcon ?: "❔", fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryName ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // source 徽标：仅账单导入（微信/支付宝）显示，手动记录零噪音（§9.3.1）。
                if (tx.source != TransactionSource.MANUAL) {
                    Spacer(Modifier.width(6.dp))
                    SourceBadge(source = tx.source)
                }
            }
            // 副文本 0~2 行：先交易对方、后退为描述（§9.3.1；均 isNotBlank 才渲染）。
            tx.counterparty?.takeIf { it.isNotBlank() }?.let { counterparty ->
                Spacer(Modifier.height(2.dp))
                Text(
                    counterparty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            tx.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                fen = tx.amount,
                semantic = if (tx.type == TransactionType.INCOME) MoneySemantic.INCOME else MoneySemantic.EXPENSE,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                TimeFormatter.formatDate(tx.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 按对象债务净额行（UI §4 C-14）。
 *
 * 状态文案/语义色口径：net > 0 → 「对方欠我」（INCOME 绿）；net < 0 → 「我欠对方」
 * （EXPENSE 红，展示绝对值）；net == 0 → 「已两清」（NET_ZERO 灰）。
 * 文案属债务域纯展示映射；快捷还款按钮由 [onRepay] 驱动（文案「还款」）。
 */
@Composable
fun PartyNetRow(
    partyName: String,
    netFen: Long,
    onRepay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = when {
        netFen > 0L -> MoneySemantic.INCOME
        netFen < 0L -> MoneySemantic.EXPENSE
        else -> MoneySemantic.NET_ZERO
    }
    val statusLabel = when {
        netFen > 0L -> "对方欠我"
        netFen < 0L -> "我欠对方"
        else -> "已两清"
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                partyName.take(1),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(partyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = semanticContentColor(semantic),
            )
        }
        MoneyText(
            fen = netFen.absoluteValue,
            semantic = semantic,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRepay) { Text("还款") }
    }
}

/**
 * 债务明细行（UI §4 C-15）。
 *
 * kind 标签 + 姓名 + 金额（±，经 [MoneyText]）+ 日期。净额贡献符号/语义色按 PRD F-02 口径：
 * `lend×owe_me=+(INCOME)`、`lend×i_owe=−(EXPENSE)`、`repay×owe_me=−(EXPENSE)`、`repay×i_owe=+(INCOME)`。
 *
 * 中文 kind 标签默认映射（可经 [kindLabel] 覆盖）：
 * 借出（lend×owe_me）/ 借入（lend×i_owe）/ 收到还款（repay×owe_me）/ 我还款（repay×i_owe）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DebtDetailRow(
    debt: Debt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kindLabel: (Debt) -> String = { standardKindLabel(it) },
    onLongClick: (() -> Unit)? = null,
) {
    val (fen, semantic) = netContribution(debt)
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(semanticContainerColor(semantic))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                kindLabel(debt),
                style = MaterialTheme.typography.labelSmall,
                color = semanticContentColor(semantic),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(debt.partyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                TimeFormatter.formatDate(debt.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(fen = fen, semantic = semantic, style = MaterialTheme.typography.titleMedium)
    }
}

/** 债务净额贡献：分（可负）+ 语义色，PRD F-02 口径。 */
private fun netContribution(debt: Debt): Pair<Long, MoneySemantic> = when {
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.OWE_ME -> debt.amount to MoneySemantic.INCOME
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.I_OWE -> -debt.amount to MoneySemantic.EXPENSE
    debt.kind == DebtKind.REPAY && debt.direction == DebtDirection.OWE_ME -> -debt.amount to MoneySemantic.EXPENSE
    else -> debt.amount to MoneySemantic.INCOME // repay × i_owe
}

/** 默认 kind 中文标签（PRD F-02），可经 [DebtDetailRow.kindLabel] 覆盖。 */
private fun standardKindLabel(debt: Debt): String = when {
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.OWE_ME -> "借出"
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.I_OWE -> "借入"
    debt.kind == DebtKind.REPAY && debt.direction == DebtDirection.OWE_ME -> "收到还款"
    else -> "我还款"
}

/** 语义 → 内容色（语义 token）。 */
@Composable
private fun semanticContentColor(semantic: MoneySemantic): Color = when (semantic) {
    MoneySemantic.INCOME -> LocalSemanticColors.current.income
    MoneySemantic.EXPENSE -> LocalSemanticColors.current.expense
    MoneySemantic.NET_ZERO -> LocalSemanticColors.current.netZero
    MoneySemantic.NONE -> LocalContentColor.current
}

/** 语义 → 弱化容器色（语义 token 12% alpha），用于标签底。 */
@Composable
private fun semanticContainerColor(semantic: MoneySemantic): Color = when (semantic) {
    MoneySemantic.INCOME -> LocalSemanticColors.current.income.copy(alpha = 0.12f)
    MoneySemantic.EXPENSE -> LocalSemanticColors.current.expense.copy(alpha = 0.12f)
    MoneySemantic.NET_ZERO -> LocalSemanticColors.current.netZero.copy(alpha = 0.12f)
    MoneySemantic.NONE -> MaterialTheme.colorScheme.surfaceVariant
}

/** 解析 DB 分类色 → Compose Color；空值/非法格式回退 fallback。 */
private fun parseCategoryHex(colorHex: String?, fallback: Color): Color =
    if (colorHex.isNullOrBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallback)
    }
