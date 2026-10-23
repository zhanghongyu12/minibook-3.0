package com.minibook.ui.budgets

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.BudgetProgress
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryBudgetProgress
import com.minibook.ui.components.CategoryGlyph
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.MoneyText
import com.minibook.ui.theme.LocalSemanticColors

/**
 * 月度总预算卡（UI §8.3）：
 * - 已设（[BudgetProgress.totalBudgetFen] > 0）：委托 [BudgetProgressCard]（总额/已用/剩余 +
 *   进度条；超支满格 `semanticExpense` 红 + 「已超支 ¥X」，DEC-012）；
 * - 未设：展示「未设置」+ 右上「设预算」按钮。
 *
 * 点击整卡（含未设态）→ 跳转账单筛当月全部支出（[onClick]）；右上「编辑总额/设预算」
 * [onEditTotal] 仍负责预算编辑。
 */
@Composable
internal fun TotalBudgetCard(
    progress: BudgetProgress,
    onEditTotal: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (progress.totalBudgetFen > 0) {
        BudgetProgressCard(
            title = "月度总预算",
            editLabel = "编辑总额",
            usedFen = progress.totalUsedFen,
            budgetFen = progress.totalBudgetFen,
            remainingFen = progress.remainingFen,
            onEdit = onEditTotal,
            onClick = onClick,
            modifier = modifier,
        )
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "月度总预算",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEditTotal) { Text("设预算") }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "未设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "点击右上方「设预算」设置本月总预算",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * 分类预算进度行（UI §8.3）：分类 IconTile（图标/名称）+ 已用/预算 + 进度条；
 * 超支进度条满格 `semanticExpense` 红 + 「已超支 ¥X」。
 *
 * 点击整行 → 跳转账单筛该分类当月支出（[onClick]）；行尾「编辑」按钮 → 弹
 * [com.minibook.ui.budgets.BudgetEditorSheet] 分类模式编辑（[onEdit]，可删除）。
 */
@Composable
internal fun CategoryBudgetRow(
    item: CategoryBudgetProgress,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BudgetProgressCard(
        title = item.category.name,
        editLabel = "编辑",
        usedFen = item.usedFen,
        budgetFen = item.budgetFen,
        remainingFen = null,
        onEdit = onEdit,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = { CategoryIconCircle(item.category) },
    )
}

/**
 * 可复用预算进度卡（总额卡与分类预算行共用的「已设」形态，UI §8.3 / DEC-012）：
 * 标题 + 编辑按钮 + 金额列（已用/预算/剩余）+ 进度条 + 超支标红。
 *
 * @param title 卡片标题（总额「月度总预算」/ 分类名）。
 * @param editLabel 右上编辑按钮文案（「编辑总额」/「编辑」）。
 * @param usedFen 已用金额（分）。
 * @param budgetFen 预算金额（分）。
 * @param remainingFen 剩余金额（分）；null=不展示「剩余」列（分类行）。
 * @param onEdit 编辑按钮回调。
 * @param onClick 整卡点击回调（跳转账单筛当月支出）。
 * @param leadingIcon 标题行前置图标（分类圆底）；null=无（总额卡）。
 */
@Composable
private fun BudgetProgressCard(
    title: String,
    editLabel: String,
    usedFen: Long,
    budgetFen: Long,
    remainingFen: Long?,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val over = budgetFen > 0 && usedFen > budgetFen
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke()
                if (leadingIcon != null) Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) { Text(editLabel) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmountColumn("已用", usedFen, modifier = Modifier.weight(1f))
                AmountColumn("预算", budgetFen, modifier = Modifier.weight(1f))
                if (remainingFen != null) {
                    AmountColumn(
                        "剩余",
                        remainingFen,
                        semantic = if (remainingFen < 0) MoneySemantic.EXPENSE else MoneySemantic.NONE,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            BudgetProgressBar(usedFen = usedFen, budgetFen = budgetFen)
            if (over) {
                Spacer(Modifier.height(8.dp))
                OverBudgetLabel(overAmountFen = usedFen - budgetFen)
            }
        }
    }
}

/** 金额列（标签 + 金额，经 [MoneyText] 展示，tnum 等宽数字）。 */
@Composable
private fun AmountColumn(
    label: String,
    fen: Long,
    modifier: Modifier = Modifier,
    semantic: MoneySemantic = MoneySemantic.NONE,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        MoneyText(fen = fen, semantic = semantic, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * 「已超支 ¥X」页内告警文案（DEC-012）：前缀文字 + 金额均用 `semanticExpense` 红。
 *
 * @param overAmountFen 超出金额（分，正值）。
 */
@Composable
private fun OverBudgetLabel(
    overAmountFen: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已超支 ",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalSemanticColors.current.expense,
        )
        MoneyText(fen = overAmountFen, semantic = MoneySemantic.EXPENSE, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 预算进度条：未超支用 `primary`（已用）/`surfaceVariant`（剩余轨道）；超支满格用
 * `semanticExpense` 红（UI §8.3 超支态 / DEC-012）。
 */
@Composable
private fun BudgetProgressBar(
    usedFen: Long,
    budgetFen: Long,
    modifier: Modifier = Modifier,
) {
    val over = budgetFen > 0 && usedFen > budgetFen
    val fraction = if (budgetFen > 0) {
        (usedFen.toFloat() / budgetFen.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { fraction },
        color = if (over) LocalSemanticColors.current.expense else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 分类图标圆底（C-03 弱化圆底，emoji 保持分类原色）。 */
@Composable
private fun CategoryIconCircle(
    category: Category,
    modifier: Modifier = Modifier,
) {
    val fallback = MaterialTheme.colorScheme.outline
    val circleColor = remember(category.color) { parseCategoryColor(category.color, fallback) }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(circleColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        CategoryGlyph(icon = category.icon, tint = circleColor, size = 24.dp, name = category.name)
    }
}

/** 解析 DB 分类色（`#RRGGBB`）→ Compose Color；空/非法回退 fallback（同 C-03 口径）。 */
private fun parseCategoryColor(hex: String?, fallback: Color): Color =
    if (hex.isNullOrBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)
    }