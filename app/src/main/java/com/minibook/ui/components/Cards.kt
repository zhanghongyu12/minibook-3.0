package com.minibook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minibook.ui.theme.MoneyLarge

/**
 * 净额卡（UI §4 C-12）：大字净额金额（tnum，经 [MoneyText]）+ 明细行 + 可选环比行。
 *
 * 适配收支结余与债务净额：标题/净额/语义色由调用方注入，明细行 [rows] 可空。
 *
 * @param balanceFen 净额（分），可为负；负值由 [MoneyText] 展示负号。
 * @param semantic 净额语义色（INCOME/EXPENSE/NET_ZERO 等）。
 * @param trend 环比行（TASK-081，可空=不展示）；见 [NetBalanceTrend.from]。
 */
@Composable
fun NetBalanceCard(
    title: String,
    balanceFen: Long,
    semantic: MoneySemantic,
    modifier: Modifier = Modifier,
    rows: List<NetBalanceRow> = emptyList(),
    trend: NetBalanceTrend? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        // 色面分层（TASK-078）：卡片用分层表面色而不是纯 surface，避免「平铺 + 描边」的旧观感。
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            MoneyText(
                fen = balanceFen,
                semantic = semantic,
                // 28sp / SemiBold / tnum：金额是这一屏的主角，不再用 36sp Regular（重心散、数字跳）。
                style = MoneyLarge,
            )
            if (trend != null) {
                Spacer(Modifier.height(6.dp))
                NetTrendLine(trend)
            }
            if (rows.isNotEmpty()) {
                // 去掉分割线，改用间距分组（TASK-078 第 3 条）。
                Spacer(Modifier.height(16.dp))
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(
                            fen = row.fen,
                            semantic = row.semantic,
                            style = MaterialTheme.typography.bodyMedium,
                            withSymbol = false,
                        )
                    }
                }
            }
        }
    }
}

/** [NetBalanceCard] 明细行数据：标签 + 金额（分）+ 语义色。 */
data class NetBalanceRow(
    val label: String,
    val fen: Long,
    val semantic: MoneySemantic,
)

/**
 * 净额卡环比数据（TASK-081 首页 Hero 卡）。
 *
 * **为什么只给绝对额、不给百分比**：上月净额为 0（或上月无记录）时百分比没有定义，
 * 硬算出「+∞%」或「+100%」是假信息。这里只展示「较上月 +¥12.34」这类**可核对**的差额；
 * 上月无记录时明确写「上月无记录」而不是编一个 0 对比（优先级「不记错」）。
 *
 * @param deltaFen 本月净额 − 上月净额（分，可为负）。
 * @param hasPrevious 上月是否有收支记录；false 时 [deltaFen] 无意义、UI 只展示文案。
 */
data class NetBalanceTrend(
    val deltaFen: Long,
    val hasPrevious: Boolean,
) {
    companion object {
        /**
         * 由本月结余与上月结余构造环比（纯函数，供单测）。
         *
         * @param currentBalanceFen 所选月结余（分）。
         * @param previousBalanceFen 上月结余（分）；null 表示上月无收支记录（见
         *   `HomeViewModel` 的判定：上月收入与支出全为 0）。
         */
        fun from(currentBalanceFen: Long, previousBalanceFen: Long?): NetBalanceTrend =
            if (previousBalanceFen == null) {
                NetBalanceTrend(deltaFen = 0L, hasPrevious = false)
            } else {
                NetBalanceTrend(
                    deltaFen = currentBalanceFen - previousBalanceFen,
                    hasPrevious = true,
                )
            }
    }
}

/**
 * 环比行（[NetBalanceTrend] 渲染）：上月无记录 / 与上月持平 / 较上月 +¥x / 较上月 ¥-x。
 *
 * 涨跌用**符号 + 颜色双编码**（只靠红绿会让色觉障碍用户读不出方向，见
 * `docs/16_auth_ux_and_visual_plan.md` §6.6）。
 */
@Composable
private fun NetTrendLine(trend: NetBalanceTrend) {
    val labelStyle = MaterialTheme.typography.bodySmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        !trend.hasPrevious -> Text("上月无记录", style = labelStyle, color = labelColor)
        trend.deltaFen == 0L -> Text("与上月持平", style = labelStyle, color = labelColor)
        else -> {
            val up = trend.deltaFen > 0L
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (up) "较上月 +" else "较上月 ",
                    style = labelStyle,
                    color = labelColor,
                )
                MoneyText(
                    fen = trend.deltaFen,
                    semantic = if (up) MoneySemantic.INCOME else MoneySemantic.EXPENSE,
                    style = labelStyle,
                )
            }
        }
    }
}
