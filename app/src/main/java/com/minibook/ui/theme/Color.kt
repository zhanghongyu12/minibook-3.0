package com.minibook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题色彩 token（UI §1.2、ARCHITECTURE §11）。
 *
 * 2026-10-12 视觉现代化（TASK-078，DEC-042）：基线配色从 M3 示例紫换成青绿色系
 * 「清爽现代」基线——浅底（近白冷灰）+ 彩色强调（青绿 primary / 蓝 tertiary），
 * 并补齐 M3 3.x 的**分层表面角色**（surfaceContainerLowest…Highest / surfaceDim /
 * surfaceBright / surfaceTint / inverse*）。
 *
 * 为什么必须补分层表面：此前全仓库 `surfaceContainer*` 0 命中，卡片与面板只能靠
 * `surfaceVariant.copy(alpha = 0.4~0.5f)` 凑层级——阴影不生效时看上去「脏、平、旧」。
 * 现在层级用色面表达（2025–2026 主流做法：无阴影 + tonal surface）。
 *
 * 语义色（[SemanticColors]）**不随动态取色漂移**：收入/支出/预警是业务语义，不是壁纸陪衬。
 *
 * API 26~30 与「自定义主题色」路径用本文件的静态基线；API 31+ 动态取色由
 * [androidx.compose.material3.dynamicLightColorScheme] 覆盖（见 ui/theme/Theme.kt）。
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F1),
    onPrimaryContainer = Color(0xFF002020),
    inversePrimary = Color(0xFF80D4D4),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF171D1D),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF171D1D),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    surfaceTint = Color(0xFF00696B),
    surfaceDim = Color(0xFFD6DBDA),
    surfaceBright = Color(0xFFF6FAF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F3),
    surfaceContainer = Color(0xFFEAEFEE),
    surfaceContainerHigh = Color(0xFFE5E9E8),
    surfaceContainerHighest = Color(0xFFDFE3E2),
    inverseSurface = Color(0xFF2C3131),
    inverseOnSurface = Color(0xFFEDF1F1),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D4),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFF9CF1F1),
    inversePrimary = Color(0xFF00696B),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4949),
    surfaceTint = Color(0xFF80D4D4),
    surfaceDim = Color(0xFF0E1514),
    surfaceBright = Color(0xFF343A39),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF171D1D),
    surfaceContainer = Color(0xFF1B2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303636),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2C3131),
)

/**
 * 语义色组（静态双色值，不走动态取色，UI §1.2）。
 *
 * 业务语义色与 M3 角色 token 解耦：收入（绿）/ 支出（红）/ 净零（中性灰）。
 * 记账场景必须「颜色 + 符号」双编码（红绿对色盲不可辨，且中文语境红涨绿跌与西方相反），
 * 故这里只提供颜色，正负号由 [com.minibook.ui.components.MoneyText] 负责。
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
    netZero = Color(0xFF6F7979),
)

/** 暗色语义色：income 亮绿 / expense 亮粉红 / netZero 亮灰（对齐 outline）。 */
val darkSemanticColors = SemanticColors(
    income = Color(0xFF4ADE80),
    expense = Color(0xFFFF8A94),
    netZero = Color(0xFF889392),
)

/**
 * 语义色 CompositionLocal：由 [MiniBookTheme] 下发（CompositionLocalProvider），
 * [com.minibook.ui.components.MoneyText] 等金额组件按语义消费；未装配时显式报错防误用。
 */
val LocalSemanticColors = staticCompositionLocalOf<SemanticColors> {
    error("SemanticColors not provided")
}
