package com.minibook.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * App 自带矢量图标集（TASK-079，DEC-042 决策 E）。
 *
 * **为什么不用 `androidx.compose.material.icons`**：仓库明确「禁改依赖」
 * （`ui/settings/LedgerEditorSheet.kt:43`，另有多处「material-icons 缺失（TASK-020 偏差）」
 * 注释），且 material3 1.4.0 起官方已把 `material.icons` 标为弃用、改推 Material Symbols
 * （需字体资源，改动面更大）。故自带 24dp/24vp 矢量，`Icon(tint = …)` 可正常着色。
 *
 * 图标路径取自 Material Symbols / Material Icons（Apache-2.0）的公开路径数据，以
 * [PathParser] 解析为节点；**不做手绘 `moveTo/curveTo` 调用**，避免路径笔误。
 *
 * 解析失败的路径会在首帧抛异常（参照 F-15o：编译绿、单测全绿仍可能运行时崩），
 * 故由 `MiniBookIconsTest` 在 JVM 侧逐个解析一遍兜底。
 */
object MiniBookIcons {

    /** 首页（底部导航）。 */
    val Home: ImageVector = icon(
        name = "MiniBook.Home",
        pathData = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    )

    /** 记账（底部导航）。 */
    val Receipt: ImageVector = icon(
        name = "MiniBook.Receipt",
        pathData = "M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2zM3 22l1.5-1.5L6 22l1.5-1.5L9 22" +
            "l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5" +
            "L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2 4.5 3.5 3 2v20z",
    )

    /** 债务（底部导航）：钱包。 */
    val Wallet: ImageVector = icon(
        name = "MiniBook.Wallet",
        pathData = "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1" +
            "h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5" +
            "s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
    )

    /** 分类（底部导航）：标签。 */
    val Label: ImageVector = icon(
        name = "MiniBook.Label",
        pathData = "M17.63 5.84C17.27 5.33 16.67 5 16 5L5 5.01C3.9 5.01 3 5.9 3 7v10c0 1.1.9 1.99 " +
            "2 1.99L16 19c.67 0 1.27-.33 1.63-.84L22 12l-4.37-6.16z",
    )

    /** 设置（底部导航）：滑杆（语义清晰、路径简单，避免齿轮长路径的笔误风险）。 */
    val Tune: ImageVector = icon(
        name = "MiniBook.Tune",
        pathData = "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7z" +
            "m14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
    )

    /** 编辑（账本管理、资料编辑等）：铅笔。 */
    val Edit: ImageVector = icon(
        name = "MiniBook.Edit",
        pathData = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41" +
            "l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    )

    /** 删除（账本管理、清空等）：垃圾桶。 */
    val Delete: ImageVector = icon(
        name = "MiniBook.Delete",
        pathData = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    )

    /** 左箭头（月份切换、返回）。 */
    val ChevronLeft: ImageVector = icon(
        name = "MiniBook.ChevronLeft",
        pathData = "M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z",
    )

    /** 右箭头（月份切换）。 */
    val ChevronRight: ImageVector = icon(
        name = "MiniBook.ChevronRight",
        pathData = "M8.59 16.59 10 18l6-6-6-6-1.41 1.41L13.17 12z",
    )

    /** 返回（二级页顶栏）。 */
    val ArrowBack: ImageVector = icon(
        name = "MiniBook.ArrowBack",
        pathData = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
    )

    /** 账号 / 用户。 */
    val Person: ImageVector = icon(
        name = "MiniBook.Person",
        pathData = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 " +
            "4v2h16v-2c0-2.66-5.33-4-8-4z",
    )

    /** 新增（Fab / 列表新增）。 */
    val Add: ImageVector = icon(
        name = "MiniBook.Add",
        pathData = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    )

    /** 显示口令（口令框 trailing 图标）。 */
    val Visibility: ImageVector = icon(
        name = "MiniBook.Visibility",
        pathData = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5" +
            "c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z" +
            "m0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
    )

    /** 隐藏口令（口令框 trailing 图标）。 */
    val VisibilityOff: ImageVector = icon(
        name = "MiniBook.VisibilityOff",
        pathData = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 " +
            "3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 " +
            "12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 " +
            "3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21" +
            "-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2" +
            ".53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3" +
            "-3l-.17.01z",
    )

    /** 搜索（首页顶栏）。 */
    val Search: ImageVector = icon(
        name = "MiniBook.Search",
        pathData = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3" +
            "S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19" +
            "l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
    )

    /** 柱状图（报表 / 快捷入口）。 */
    val BarChart: ImageVector = icon(
        name = "MiniBook.BarChart",
        pathData = "M5 9.2h3V19H5V9.2zM10.6 5h2.8v14h-2.8V5zm5.6 8H19v6h-2.8v-6z",
    )

    /** 旗帜 / 目标（预算快捷入口）。 */
    val Flag: ImageVector = icon(
        name = "MiniBook.Flag",
        pathData = "M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6h-5.6z",
    )

    /** 下载（导入账单快捷入口）。 */
    val Download: ImageVector = icon(
        name = "MiniBook.Download",
        pathData = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
    )

    /** 供单测遍历：全部图标（名称 → 矢量）。 */
    internal val all: Map<String, ImageVector> = mapOf(
        "Home" to Home,
        "Receipt" to Receipt,
        "Wallet" to Wallet,
        "Label" to Label,
        "Tune" to Tune,
        "Edit" to Edit,
        "Delete" to Delete,
        "ChevronLeft" to ChevronLeft,
        "ChevronRight" to ChevronRight,
        "ArrowBack" to ArrowBack,
        "Person" to Person,
        "Add" to Add,
        "Visibility" to Visibility,
        "VisibilityOff" to VisibilityOff,
        "Search" to Search,
        "BarChart" to BarChart,
        "Flag" to Flag,
        "Download" to Download,
    )

    /** 以 SVG 路径数据构建 24dp 图标（填充黑色，实际颜色由 `Icon(tint = …)` 覆盖）。 */
    private fun icon(name: String, pathData: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
