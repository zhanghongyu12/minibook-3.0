package com.minibook.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.ui.theme.TabularFigures
import com.minibook.util.MoneyFormatter

/** 金额语义色枚举：决定 [MoneyText] 文本颜色（UI §4）。 */
enum class MoneySemantic {

    /** 不染色，跟随 [LocalContentColor]（默认）。 */
    NONE,

    /** 收入（语义色 income，绿）。 */
    INCOME,

    /** 支出（语义色 expense，红）。 */
    EXPENSE,

    /** 净零 / 中性（语义色 netZero，灰）。 */
    NET_ZERO,
}

/**
 * 金额展示组件（UI §4、TASK-019）。
 *
 * 业务代码禁止手写金额格式化（`String.format` / `DecimalFormat` / 手工拼接），统一走本组件：
 * 内部经 [MoneyFormatter.formatFen] 完成分→元格式化，并 `style.merge([TabularFigures])`
 * 开启等宽数字。颜色由 [MoneySemantic] 决定：语义色经 [LocalSemanticColors] 取当前主题值
 * （静态双色值，UI §1.2），[MoneySemantic.NONE] 跟随 [LocalContentColor]。
 *
 * @param fen 金额（分，Long）。
 * @param withSymbol 是否带 `¥` 前缀（默认带）。
 * @param semantic 语义色（收入/支出/净零/无）。
 * @param style 文本样式，默认 titleMedium。
 */
@Composable
fun MoneyText(
    fen: Long,
    modifier: Modifier = Modifier,
    withSymbol: Boolean = true,
    semantic: MoneySemantic = MoneySemantic.NONE,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val semanticColors = LocalSemanticColors.current
    val color: Color = when (semantic) {
        MoneySemantic.NONE -> LocalContentColor.current
        MoneySemantic.INCOME -> semanticColors.income
        MoneySemantic.EXPENSE -> semanticColors.expense
        MoneySemantic.NET_ZERO -> semanticColors.netZero
    }
    Text(
        text = MoneyFormatter.formatFen(fen, withSymbol),
        modifier = modifier,
        style = style.merge(TabularFigures),
        color = color,
    )
}
