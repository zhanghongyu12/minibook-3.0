package com.minibook.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.MoneyText
import com.minibook.util.TimeFormatter

/**
 * 债务只读明细 Sheet（TASK-033，UI §8.1「点击结果弹只读明细，无编辑/保存/删除」）。
 *
 * 复用 [com.minibook.ui.sheets.DebtSheet] 的展示形态，仅去保存路径——因边界限制无法修改
 * `ui/sheets/DebtSheet.kt`（其与 [com.minibook.ui.sheets.DebtSheetViewModel] 强耦合、且含
 * 保存/删除路径），故在 `ui/search/` 内新建本只读子组件，按 ARCHITECTURE §15.2 落地。
 *
 * 展示槽：kind×direction + 姓名 + 金额（±、按净额贡献语义色，PRD F-02）+ 日期 + 备注；无保存/无删除。
 *
 * @param debt 目标债务（搜索结果行点击注入）。
 * @param onDismiss 关闭回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailSheet(
    debt: Debt,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
        ) {
            Text("债务明细", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            DetailRow(label = "类型") {
                Text(debtKindLabel(debt), style = MaterialTheme.typography.bodyLarge)
            }
            DetailRow(label = "对方") {
                Text(debt.partyName, style = MaterialTheme.typography.bodyLarge)
            }
            DetailRow(label = "金额") {
                val (fen, semantic) = debtNetContribution(debt)
                MoneyText(fen = fen, semantic = semantic, style = MaterialTheme.typography.titleMedium)
            }
            DetailRow(label = "日期") {
                Text(TimeFormatter.formatDate(debt.date), style = MaterialTheme.typography.bodyLarge)
            }
            DetailRow(label = "备注") {
                Text(
                    debt.remark ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (debt.remark.isNullOrBlank()) {
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

/** kind×direction → 中文标签（PRD F-02）：借出/借入/收到还款/我还款。 */
private fun debtKindLabel(debt: Debt): String = when {
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.OWE_ME -> "借出"
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.I_OWE -> "借入"
    debt.kind == DebtKind.REPAY && debt.direction == DebtDirection.OWE_ME -> "收到还款"
    else -> "我还款"
}

/** 债务净额贡献：分（可负）+ 语义色（PRD F-02）。 */
private fun debtNetContribution(debt: Debt): Pair<Long, MoneySemantic> = when {
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.OWE_ME -> debt.amount to MoneySemantic.INCOME
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.I_OWE -> -debt.amount to MoneySemantic.EXPENSE
    debt.kind == DebtKind.REPAY && debt.direction == DebtDirection.OWE_ME -> -debt.amount to MoneySemantic.EXPENSE
    else -> debt.amount to MoneySemantic.INCOME // repay × i_owe
}
