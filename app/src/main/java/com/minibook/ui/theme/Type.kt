package com.minibook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/**
 * 字号/字重轨（UI §1.3、TASK-019）。
 *
 * 直接采用 M3 默认 [Typography]（系统默认字体 FontFamily.Default，DEC-001 无字体依赖），
 * 不覆盖——M3 默认 scale 已满足 UI §1.3 字号轨（12/14/16/20/24/34/45…）。
 */
val MiniBookTypography: Typography = Typography()

/**
 * 等宽数字（tabular figures）样式 token。
 *
 * 开启 `tnum` 字体特性，让金额数字各字符等宽对齐（避免小数点两侧跳动），供
 * [com.minibook.ui.components.MoneyText] 与金额大字 `style.merge(TabularFigures)` 复用。
 */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")
