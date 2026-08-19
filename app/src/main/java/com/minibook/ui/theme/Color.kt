package com.minibook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题色彩 token（UI §1.2、ARCHITECTURE §11）。
 *
 * M3 角色 token 分亮/暗两套基线（[LightColors] / [DarkColors]），作为 API 26~30 降级用
 * 静态配色；API 31+ 由 [androidx.compose.material3.dynamicLightColorScheme] /
 * [androidx.compose.material3.dynamicDarkColorScheme] 覆盖（见 ui/theme/Theme.kt）。
 * 其余 M3 默认色走框架内置默认值，不在此覆盖。
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A4458),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
)

/**
 * 语义色组（静态双色值，不走动态取色，UI §1.2）。
 *
 * 业务语义色与 M3 角色 token 解耦：收入（绿）/ 支出（红）/ 净零（中性灰）。
 */
data class SemanticColors(
    val income: Color,
    val expense: Color,
    val netZero: Color,
)

/** 亮色语义色：income 深绿 / expense 深红 / netZero 灰（对齐 outline）。 */
val lightSemanticColors = SemanticColors(
    income = Color(0xFF1B7F3B),
    expense = Color(0xFFC62828),
    netZero = Color(0xFF79747E),
)

/** 暗色语义色：income 亮绿 / expense 亮粉红 / netZero 亮灰（对齐 outline）。 */
val darkSemanticColors = SemanticColors(
    income = Color(0xFF4ADE80),
    expense = Color(0xFFFF8A94),
    netZero = Color(0xFF938F99),
)

/**
 * 语义色 CompositionLocal：由 [MiniBookTheme] 下发（CompositionLocalProvider），
 * [com.minibook.ui.components.MoneyText] 等金额组件按语义消费；未装配时显式报错防误用。
 */
val LocalSemanticColors = staticCompositionLocalOf<SemanticColors> {
    error("SemanticColors not provided")
}
