package com.minibook.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.minibook.ui.theme.FullShape

/**
 * 底部浮动「＋」按钮（UI §4 C-06）。
 *
 * 全圆角 [FloatingActionButton]，内部用自带矢量图标（[MiniBookIcons.Add]，不依赖 material-icons 库）。
 *
 * @param contentDescription 无障碍描述，默认「新增」。
 */
@Composable
fun PrimaryFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "新增",
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = FullShape,
    ) {
        Icon(imageVector = MiniBookIcons.Add, contentDescription = null)
    }
}

/**
 * 底部导航项封装（UI §4 C-07）。
 *
 * 封装 [NavigationBarItem]（material3 中为 `RowScope` 扩展，故本组件同为 `RowScope`
 * 扩展，需置于 [androidx.compose.material3.NavigationBar] 内容槽内使用）。
 * 状态自外下行（[selected]），无内部业务逻辑。
 *
 * @param route 目标路由；不参与组件渲染，供调用方在 NavHost 配置 `saveState`/`restoreState`
 *   时使用（`navigate(route) { launchSingleTop = true; saveState = true; restoreState = true }`）。
 */
@Composable
fun RowScope.BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    route: String,
    modifier: Modifier = Modifier,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        modifier = modifier,
    )
}
