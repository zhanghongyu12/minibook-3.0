package com.minibook.ui.search

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.MoneyText
import com.minibook.ui.components.chineseLabel
import com.minibook.util.TimeFormatter

/**
 * 交易只读明细 Sheet（TASK-033，UI §8.1「点击结果弹只读明细，无编辑/保存/删除」）。
 *
 * 复用 [com.minibook.ui.sheets.TransactionSheet] 的展示形态，仅去保存路径——因边界限制
 * 无法修改 `ui/sheets/TransactionSheet.kt`（其与 [com.minibook.ui.sheets.TransactionSheetViewModel]
 * 强耦合、且含保存/删除路径），故在 `ui/search/` 内新建本只读子组件，按
 * ARCHITECTURE §15.2「复用展示部分、去掉保存路径」落地。
 *
 * 展示槽：类型（支出/收入）+ 金额（经 [MoneyText]，语义色）+ 分类（emoji + 名称，DB 色）+ 日期 + 备注；
 * 无保存/无删除，底部仅「关闭」。
 *
 * @param tx 目标交易（搜索结果行点击注入）。
 * @param categoryName 分类名；空则「未分类」占位。
 * @param categoryIcon 分类 emoji；空则「❔」。
 * @param categoryColor 分类色（`#RRGGBB`）；空则回退 outline。
 * @param onDismiss 关闭回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    tx: Transaction,
    categoryName: String?,
    categoryIcon: String?,
    categoryColor: String?,
    onDismiss: () -> Unit,
) {
    val fallback = MaterialTheme.colorScheme.outline
    val iconColor = remember(categoryColor) { parseHex(categoryColor, fallback) }
    val icon = categoryIcon ?: "❔"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
        ) {
            Text("交易明细", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            // 类型 + 金额
            DetailRow(label = "类型") {
                Text(
                    if (tx.type == TransactionType.INCOME) "收入" else "支出",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            DetailRow(label = "金额") {
                MoneyText(
                    fen = tx.amount,
                    semantic = if (tx.type == TransactionType.INCOME) MoneySemantic.INCOME else MoneySemantic.EXPENSE,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            DetailRow(label = "分类") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 18.sp, color = iconColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        categoryName ?: "未分类",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DetailRow(label = "日期") {
                Text(TimeFormatter.formatDate(tx.date), style = MaterialTheme.typography.bodyLarge)
            }
            // 导入字段信息块（§9.3.2）：于「日期」后、「备注」前聚簇插入，空值用「—」弱化。
            DetailRow(label = "对方") {
                Text(
                    text = tx.counterparty?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tx.counterparty.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            DetailRow(label = "支付方式") {
                Text(
                    text = tx.paymentMethod?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tx.paymentMethod.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            DetailRow(label = "来源") {
                Text(tx.source.chineseLabel, style = MaterialTheme.typography.bodyLarge)
            }
            DetailRow(label = "备注") {
                Text(
                    tx.description ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tx.description.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}

/**
 * 只读明细行：左侧标签 + 右侧值槽。
 *
 * 交易/债务两份明细复用（同包直接可见），`label` 列定宽、`value` 弹性。
 * 参数顺序：`value` 置末以支持 trailing lambda（`DetailRow("标签") { ... }`）。
 */
@Composable
internal fun DetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Box(Modifier.weight(1f)) { value() }
    }
}

/** 解析 DB 分类色（`#RRGGBB`）→ Compose Color；空值/非法格式回退 fallback。 */
internal fun parseHex(colorHex: String?, fallback: Color): Color =
    if (colorHex.isNullOrBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallback)
    }
