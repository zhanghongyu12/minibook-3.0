package com.minibook.ui.billimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.ParseSkippedRow

/**
 * 账单导入（F-12 / P-15）新增组件 C-34~C-36（UI §9.5）。
 *
 * 组件不携带业务文案（§4 约定）：文案由 [ImportScreen] 精确注入（§9.6 文案表）。色值全部
 * token 化（§1 / §9.2 暗色适配）。
 *
 * 包名说明：设计稿 §9.5 记「落 `ui/import/`」，但 `import` 为 Java/Kotlin 关键字、不可作包段
 * （KSP 报错），故以最接近的合法包名 `com.minibook.ui.billimport`（目录 `ui/billimport/`）落地。
 *
 * TODO: C-37 `SourceFilterChipRow`（收支页来源筛选，UI §9.4）为可选增强，本期（TASK-044）未做，
 *  待后续扩展 `TransactionFilter.source` 后落地（§9.4 推荐方案）。
 */
private const val DEFAULT_PARSE_ERROR_EXPAND_THRESHOLD: Int = 3

/**
 * 单平台导出指引卡（UI §9.5 C-34）。
 *
 * emoji + 平台名（[titleMedium]）+ 分步导出路径文本（`bodySmall`，[onSurfaceVariant]）+
 * 文件格式提示（`labelMedium`）；底面 `surfaceVariant.copy(alpha=0.5f)`、`medium` 圆角
 * （同 `HomeQuickEntry` 轻量卡片口径）。
 *
 * @param emoji 平台图标（emoji 占位，TASK-020 偏差同口径）。
 * @param title 平台名（如「微信账单」）。
 * @param path 分步导出路径文案。
 * @param format 文件格式提示（如「导出格式：.xlsx（账单明细）」）。
 * @param onClick 卡片点击回调（默认无操作；导入页接线为触发 SAF 文件选择）。
 */
@Composable
fun PlatformGuideCard(
    emoji: String,
    title: String,
    path: String,
    format: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                format,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 结果摘要卡（UI §9.5 C-35）。
 *
 * 主数字：`success > 0` → 「成功导入 N 笔」（`headlineMedium`，`primary` 强调）；
 * `success == 0` → 「本次无新增交易」（`onSurfaceVariant` 弱化不红）。次级行「跳过 M 笔」；
 * 全跳过（N=0 且 M>0）追加副文案「均被跳过，请查看下方明细。」。
 *
 * @param result 导入结果聚合（计数由各字段求和，不重复计算）。
 */
@Composable
fun ImportSummaryCard(
    result: BillImportResult,
    modifier: Modifier = Modifier,
) {
    val skipped = result.existing + result.neutral + result.closed + result.zeroAmount + result.parseErrors.size
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (result.success > 0) {
                Text(
                    text = "成功导入 ${result.success} 笔",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "本次无新增交易",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "跳过 $skipped 笔",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.success == 0 && skipped > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "均被跳过，请查看下方明细。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 跳过分原因计数区（UI §9.5 C-36）。
 *
 * 五行固定计数（已存在 / 不计收支 / 交易关闭 / 0元订单 / 解析失败，§9.6「未命中即 0 笔仍占位保五行齐整」）；
 * 「解析失败」为可折叠分组：`≤3` 条默认展开、`>3` 默认折叠，展开逐条「第 k 行 · 原因」
 * （k = [ParseSkippedRow.rowNumber]，原因按 [parseErrorLabel] 映射为精确中文，§9.6）。
 *
 * @param result 导入结果聚合；`parseErrors` 供折叠明细逐条展示。
 */
@Composable
fun SkippedBreakdownSection(
    result: BillImportResult,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            ReasonCountRow(label = "已存在", count = result.existing)
            ReasonCountRow(label = "不计收支", count = result.neutral)
            ReasonCountRow(label = "交易关闭", count = result.closed)
            ReasonCountRow(label = "0元订单", count = result.zeroAmount)
            ParseErrorBlock(parseErrors = result.parseErrors)
        }
    }
}

/** 单条分原因计数行：标签左（`onSurfaceVariant`）＋「X 笔」右。 */
@Composable
private fun ReasonCountRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count 笔",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 「解析失败」计数行 + 折叠明细（UI §9.2 解析失败明细形态，DEC-020 自决）。
 *
 * 行末计数文案：折叠态含提示「W 笔（点击展开）」（§9.6 折叠表头精确文案），展开态「W 笔」；
 * 尾随 `▶/▼` 指示。`count == 0` 不渲染指示、不可点（占位行）。`≤3` 条默认展开
 * （[DEFAULT_PARSE_ERROR_EXPAND_THRESHOLD] 阈值），状态以 `remember(count)` 随结果重建。
 */
@Composable
private fun ParseErrorBlock(parseErrors: List<ParseSkippedRow>) {
    val count = parseErrors.size
    // ≤3 条默认展开（少量无折叠必要，§9.2）；>3 条默认折叠。
    var expanded by remember(count) {
        mutableStateOf(count <= DEFAULT_PARSE_ERROR_EXPAND_THRESHOLD)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (count > 0) it.clickable { expanded = !expanded } else it }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "解析失败",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (count > 0 && !expanded) "$count 笔（点击展开）" else "$count 笔",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "▼" else "▶",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded && count > 0) {
            Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                parseErrors.forEach { row ->
                    Text(
                        text = "第 ${row.rowNumber} 行 · ${parseErrorLabel(row.detail)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 解析失败 `detail` → 精确中文原因（§9.6 文案表）。
 *
 * 解析器（微信/支付宝）产出 3 类 `PARSE_ERROR`（detail 前缀固定），据此映射：
 * 交易时间 → 「缺少交易时间」、金额 → 「金额非法或非正数」、单号 → 「缺少交易单号」；
 * 无法命中时防御性直显 `detail`（源码数据，非自创文案）。
 */
private fun parseErrorLabel(detail: String): String = when {
    "交易时间" in detail -> "缺少交易时间"
    "金额" in detail -> "金额非法或非正数"
    "单号" in detail || "订单号" in detail -> "缺少交易单号"
    else -> detail
}