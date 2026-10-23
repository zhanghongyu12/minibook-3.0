package com.minibook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.datastore.DarkModeSetting
import com.minibook.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 主题装配 ViewModel（TASK-019 专属极小 VM）。
 *
 * 仅暴露 [SettingsDataStore.darkMode] 一个只读流，供 [MiniBookTheme] 软切换暗色模式
 * （ARCHITECTURE §11）。不承载 TASK-027 设置页的任何其他设置逻辑。
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore,
) : ViewModel() {

    /** 暗色模式三态（无值默认 [DarkModeSetting.SYSTEM] 跟随系统）。 */
    val darkMode: StateFlow<DarkModeSetting> = settingsDataStore.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DarkModeSetting.SYSTEM)

    /** 自定义主题色（ARGB hex），无值 null = 跟随默认/动态取色。 */
    val themeColor: StateFlow<String?> = settingsDataStore.themeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * 形状 token（UI §1.5）：组件圆角基准。
 *
 * 2026-10-12 视觉现代化（TASK-078，DEC-042）：整体放大一档——卡片（`large`）20dp、
 * 次级容器（`medium`）16dp、小控件（`small`）12dp，用于脱离「默认 M3」观感。
 *
 * 注意记账场景的**信息密度红线**（doc §6.6 反例 1）：圆角给卡片，**行内间距仍走 4/8dp 网格**，
 * 不得用大留白换「高级感」——一屏流水从 12 条掉到 6 条是不能接受的代价。
 */
val MiniBookShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 「全圆角」形状 token（UI §1.5）：28dp。
 *
 * M3 [Shapes] 无 `full` 槽位（仅 extraSmall/small/medium/large/extraLarge），故以独立
 * token 提供，供弹层 / 底部抽屉等全圆角容器复用（28dp = M3 大号容器标准圆角）。
 */
val FullShape = RoundedCornerShape(28.dp)

/**
 * 主题入口（TASK-019，ARCHITECTURE §11）。
 *
 * 内部自取暗色设置：默认跟随 DataStore [SettingsDataStore.darkMode]（[ThemeViewModel]），
 * 也可经 [darkTheme] 可选覆盖。API 31+ 且 [dynamicColor] 开启时用系统动态取色
 * （WallpaperColors），否则回退 [LightColors] / [DarkColors] 基线；语义色按明暗
 * （[darkSemanticColors] / [lightSemanticColors]）经 [LocalSemanticColors] 下发。
 *
 * 系统栏 edge-to-edge 由 [MainActivity.onCreate] 的 `enableEdgeToEdge()` 统一处理
 * （TASK-019 决定，避免重复设置），本组合件不再调用。
 *
 * @param darkTheme 可选暗色覆盖；null 时由 DataStore + 系统当前配置共同决定。
 * @param dynamicColor 是否启用 Android 12+（API 31+）系统动态取色。
 */
@Composable
fun MiniBookTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ThemeViewModel = hiltViewModel()
    val setting by viewModel.darkMode.collectAsStateWithLifecycle()
    val themeColorHex by viewModel.themeColor.collectAsStateWithLifecycle()

    val resolvedDark = darkTheme ?: when (setting) {
        DarkModeSetting.SYSTEM -> isSystemInDarkTheme()
        DarkModeSetting.LIGHT -> false
        DarkModeSetting.DARK -> true
    }
    val customPrimary = themeColorHex?.let(::parseThemeColor)
    val colorScheme = when {
        // 自定义主题色优先于 dynamicColor / 静态基线：以 seed 生成完整
        // primary/secondary/tertiary 及各自 container/on，容器色/徽标/FAB/菜单栏等自动跟随。
        customPrimary != null ->
            if (resolvedDark) themedDarkScheme(customPrimary) else themedLightScheme(customPrimary)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        resolvedDark -> DarkColors
        else -> LightColors
    }
    val semanticColors = if (resolvedDark) darkSemanticColors else lightSemanticColors

    CompositionLocalProvider(LocalSemanticColors.provides(semanticColors)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MiniBookTypography,
            shapes = MiniBookShapes,
            content = content,
        )
    }
}

/** 解析 ARGB hex（如 `"#6750A4"`）为 Compose [Color]；非法输入回退 null（保持默认取色）。 */
private fun parseThemeColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()

/**
 * 以 [seed] 生成完整亮色 [ColorScheme]（primary/secondary/tertiary 及各自 container/on）。
 *
 * 用 [lerp] 做色相调和：container 由 [LightColors] 的 surfaceContainer 向 seed 轻混、
 * on-container 由容器的**实测亮度**决定黑白（而不是恒用白字）——恒定 `onPrimary = Color.White`
 * 在浅色 seed 下对比度不足（WCAG 1.4.3 正文 4.5:1，TASK-078 修正）。
 *
 * 中性面（surface / surfaceContainerLowest…Highest / outline / error）一律沿用 [LightColors]
 * 基线：自定义主题色只换强调色，不换「浅底 + 分层表面」这套骨架。
 */
private fun themedLightScheme(seed: Color): ColorScheme {
    val primaryContainer = lerp(LightColors.surfaceContainer, seed, 0.24f)
    val secondary = lerp(seed, Color.Black, 0.08f)
    val secondaryContainer = lerp(LightColors.surfaceContainer, seed, 0.16f)
    val tertiary = lerp(seed, Color.Black, 0.18f)
    val tertiaryContainer = lerp(LightColors.surfaceContainer, seed, 0.12f)
    return lightColorScheme(
        primary = seed,
        onPrimary = onColorFor(seed),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onColorFor(primaryContainer),
        inversePrimary = lerp(seed, Color.White, 0.55f),
        secondary = secondary,
        onSecondary = onColorFor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onColorFor(secondaryContainer),
        tertiary = tertiary,
        onTertiary = onColorFor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColorFor(tertiaryContainer),
        error = LightColors.error,
        onError = LightColors.onError,
        errorContainer = LightColors.errorContainer,
        onErrorContainer = LightColors.onErrorContainer,
        background = LightColors.background,
        onBackground = LightColors.onBackground,
        surface = LightColors.surface,
        onSurface = LightColors.onSurface,
        surfaceVariant = LightColors.surfaceVariant,
        onSurfaceVariant = LightColors.onSurfaceVariant,
        outline = LightColors.outline,
        outlineVariant = LightColors.outlineVariant,
        surfaceTint = seed,
        surfaceDim = LightColors.surfaceDim,
        surfaceBright = LightColors.surfaceBright,
        surfaceContainerLowest = LightColors.surfaceContainerLowest,
        surfaceContainerLow = LightColors.surfaceContainerLow,
        surfaceContainer = LightColors.surfaceContainer,
        surfaceContainerHigh = LightColors.surfaceContainerHigh,
        surfaceContainerHighest = LightColors.surfaceContainerHighest,
        inverseSurface = LightColors.inverseSurface,
        inverseOnSurface = LightColors.inverseOnSurface,
    )
}

/**
 * 以 [seed] 生成完整暗色 [ColorScheme]（primary/secondary/tertiary 及各自 container/on）。
 *
 * 暗色下 primary/secondary/tertiary 向白提亮、container 向黑压深，on-X 同样按实测亮度选黑白；
 * 中性面沿用 [DarkColors]（tonal 深灰而非纯黑——纯黑底 + 高饱和强调色会眩光）。
 */
private fun themedDarkScheme(seed: Color): ColorScheme {
    val primary = lerp(seed, Color.White, 0.60f)
    val primaryContainer = lerp(seed, Color.Black, 0.55f)
    val secondary = lerp(seed, Color.White, 0.35f)
    val secondaryContainer = lerp(seed, Color.Black, 0.60f)
    val tertiary = lerp(seed, Color.White, 0.20f)
    val tertiaryContainer = lerp(seed, Color.Black, 0.65f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onColorFor(primaryContainer),
        inversePrimary = lerp(seed, Color.Black, 0.20f),
        secondary = secondary,
        onSecondary = onColorFor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onColorFor(secondaryContainer),
        tertiary = tertiary,
        onTertiary = onColorFor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColorFor(tertiaryContainer),
        error = DarkColors.error,
        onError = DarkColors.onError,
        errorContainer = DarkColors.errorContainer,
        onErrorContainer = DarkColors.onErrorContainer,
        background = DarkColors.background,
        onBackground = DarkColors.onBackground,
        surface = DarkColors.surface,
        onSurface = DarkColors.onSurface,
        surfaceVariant = DarkColors.surfaceVariant,
        onSurfaceVariant = DarkColors.onSurfaceVariant,
        outline = DarkColors.outline,
        outlineVariant = DarkColors.outlineVariant,
        surfaceTint = primary,
        surfaceDim = DarkColors.surfaceDim,
        surfaceBright = DarkColors.surfaceBright,
        surfaceContainerLowest = DarkColors.surfaceContainerLowest,
        surfaceContainerLow = DarkColors.surfaceContainerLow,
        surfaceContainer = DarkColors.surfaceContainer,
        surfaceContainerHigh = DarkColors.surfaceContainerHigh,
        surfaceContainerHighest = DarkColors.surfaceContainerHighest,
        inverseSurface = DarkColors.inverseSurface,
        inverseOnSurface = DarkColors.inverseOnSurface,
    )
}

/**
 * 按背景**实测亮度**挑前景色：亮底用近黑、暗底用白（阈值 0.5，TASK-078）。
 *
 * 用于自定义主题色的 `onPrimary/onSecondary/onTertiary` 等角色，避免浅色 seed 配白字
 * 导致 WCAG 1.4.3 对比度不足（旧实现恒定 `Color.White`）。
 */
private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF101919) else Color.White
