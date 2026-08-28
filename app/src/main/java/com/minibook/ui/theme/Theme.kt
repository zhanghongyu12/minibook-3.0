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

/** 形状 token（UI §1.5）：组件圆角基准。 */
val MiniBookShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 「全圆角」形状 token（UI §1.5）：24dp。
 *
 * M3 [Shapes] 无 `full` 槽位（仅 extraSmall/small/medium/large/extraLarge），故以独立
 * token 提供，供弹层 / 底部抽屉等全圆角容器复用。
 */
val FullShape = RoundedCornerShape(24.dp)

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
 * 用 [lerp] 做色相调和：container 由 surface 向 seed 轻混、on-container 由 seed 向黑轻混。
 * surface/background/error 系列保持默认（中性色、错误红不跟随主题色）。
 */
private fun themedLightScheme(seed: Color): ColorScheme = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = lerp(LightColors.surface, seed, 0.16f),
    onPrimaryContainer = lerp(seed, Color.Black, 0.25f),
    secondary = lerp(seed, Color.Black, 0.08f),
    onSecondary = Color.White,
    secondaryContainer = lerp(LightColors.surface, seed, 0.12f),
    onSecondaryContainer = lerp(seed, Color.Black, 0.25f),
    tertiary = lerp(seed, Color.Black, 0.18f),
    onTertiary = Color.White,
    tertiaryContainer = lerp(LightColors.surface, seed, 0.10f),
    onTertiaryContainer = lerp(seed, Color.Black, 0.25f),
)

/**
 * 以 [seed] 生成完整暗色 [ColorScheme]（primary/secondary/tertiary 及各自 container/on）。
 *
 * 暗色下 primary/secondary/tertiary 向白提亮、container 向黑压深，on-container 向白提亮。
 */
private fun themedDarkScheme(seed: Color): ColorScheme = darkColorScheme(
    primary = lerp(seed, Color.White, 0.60f),
    onPrimary = lerp(seed, Color.Black, 0.20f),
    primaryContainer = lerp(seed, Color.Black, 0.55f),
    onPrimaryContainer = lerp(seed, Color.White, 0.80f),
    secondary = lerp(seed, Color.White, 0.35f),
    onSecondary = lerp(seed, Color.Black, 0.20f),
    secondaryContainer = lerp(seed, Color.Black, 0.60f),
    onSecondaryContainer = lerp(seed, Color.White, 0.75f),
    tertiary = lerp(seed, Color.White, 0.20f),
    onTertiary = lerp(seed, Color.Black, 0.20f),
    tertiaryContainer = lerp(seed, Color.Black, 0.65f),
    onTertiaryContainer = lerp(seed, Color.White, 0.75f),
)
