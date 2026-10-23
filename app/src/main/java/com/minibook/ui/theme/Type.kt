package com.minibook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/**
 * 金额大字 token（TASK-078）：28sp / SemiBold / 等宽数字。
 *
 * 净额卡、首页 Hero 卡等「一眼看金额」的位置用它——原实现用 `displaySmall`
 * （36sp Regular，`ui/components/Cards.kt`）过大且无字重，视觉重心散、数字还会跳。
 */
val MoneyLarge = TextStyle(
    fontSize = 28.sp,
    lineHeight = 36.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/** 金额中字 token（TASK-078）：20sp / SemiBold / 等宽数字，用于列表与卡内次要金额。 */
val MoneyMedium = TextStyle(
    fontSize = 20.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)
