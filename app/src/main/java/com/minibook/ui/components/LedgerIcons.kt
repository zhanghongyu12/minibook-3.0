package com.minibook.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 账本矢量图标集（2026-10-13，用户原话：「账本的图标也换一下」）。
 *
 * 与 [CategoryIcons] 同一套做法：Material Symbols Rounded 公开路径数据（Apache-2.0），
 * `addPathNodes` 解析，24×24 网格，单色，由调用方用账本色或主题色 `tint` 着色。
 *
 * **存储口径**：`Ledger.icon` 仍是 String 列（`data/local/db/entity/LedgerEntity.kt`），
 * 这里存的是 [BOOK] 之类的稳定 key；**不改 Room schema、不做数据迁移**。
 * 老数据（以及老备份）里存的是 emoji，[resolve] 的 `LEGACY_EMOJI` 把账本编辑器历史上的 16 个
 * emoji 映射到同一批图标，因此升级后旧账本自动显示新图标；用户自己手输的 emoji 走兜底原样渲染。
 *
 * 路径解析失败只在首帧抛异常（参照 F-15o），故由 `LedgerIconsTest` 在 JVM 侧逐个解析一遍兜底。
 */
object LedgerIcons {

    // —— 稳定 key：写进 `Ledger.icon`，一旦发布不得改动 ——
    const val BOOK = "book"
    const val NOTEBOOK = "notebook"
    const val JOURNAL = "journal"
    const val STORIES = "stories"
    const val LIBRARY = "library"
    const val NOTES = "notes"
    const val RECEIPT = "receipt"
    const val STACK = "stack"
    const val WORK = "work"
    const val SAVINGS = "savings"
    const val BANK = "bank"
    const val CARD = "card"
    const val CHART = "chart"
    const val SHOPPING = "shopping"
    const val HOME = "home"
    const val CAR = "car"

    /** 账本(默认)（历史 emoji 📒）。 */
    val Book: ImageVector = icon(
        name = "Ledger.Book",
            "M6 22q-.825 0-1.412-.587T4 20V4q0-.825.588-1.412T6 2h12q.825 0 1.413.588T20 4v16" +
            "q0 .825-.587 1.413T18 22zm5-18v6.125q0 .3.238.438t.512-.013l1.225-.725q.25-.15.5" +
            "13-.15t.512.15l1.225.725q.275.15.525.013t.25-.438V4z",
    )

    /** 笔记本（历史 emoji 📓）。 */
    val Notebook: ImageVector = icon(
        name = "Ledger.Notebook",
            "M7.5 22q-1.45 0-2.475-1.025T4 18.5v-13q0-1.45 1.025-2.475T7.5 2H18q.825 0 1.413." +
            "587T20 4v12.525q0 .2-.162.363t-.588.362q-.35.175-.55.5t-.2.75t.2.763t.55.487t.55" +
            ".413t.2.562v.25q0 .425-.288.725T19 22zm2.213-7.288Q10 14.425 10 14V5q0-.425-.288" +
            "-.712T9 4t-.712.288T8 5v9q0 .425.288.713T9 15t.713-.288M7.5 20h9.325q-.15-.35-.2" +
            "37-.712T16.5 18.5q0-.4.075-.775t.25-.725H7.5q-.65 0-1.075.438T6 18.5q0 .65.425 1" +
            ".075T7.5 20",
    )

    /** 日记本（历史 emoji 📔）。 */
    val Journal: ImageVector = icon(
        name = "Ledger.Journal",
            "M13 17.05q1.1-.525 2.213-.788T17.5 16q.9 0 1.763.15T21 16.6V6.7q-.825-.35-1.713-" +
            ".525T17.5 6q-1.175 0-2.325.3T13 7.2zm-1.662 2.338q-.313-.088-.588-.238q-.975-.57" +
            "5-2.05-.862T6.5 18q-1.05 0-2.062.275T2.5 19.05q-.525.275-1.012-.025T1 18.15V6.1q" +
            "0-.275.138-.525T1.55 5.2q1.175-.575 2.413-.888T6.5 4q1.45 0 2.838.375T12 5.5q1.2" +
            "75-.75 2.663-1.125T17.5 4q1.3 0 2.538.313t2.412.887q.275.125.413.375T23 6.1v12.0" +
            "5q0 .575-.487.875t-1.013.025q-.925-.5-1.937-.775T17.5 18q-1.125 0-2.2.288t-2.05." +
            "862q-.275.15-.587.238t-.663.087t-.663-.088M14 8.776q0-.225.163-.462T14.525 8q.72" +
            "5-.25 1.45-.375T17.5 7.5q.5 0 .988.063t.962.162q.225.05.388.25t.162.45q0 .425-.2" +
            "75.625t-.7.1q-.35-.075-.737-.112T17.5 9q-.65 0-1.275.125t-1.2.325q-.45.175-.737-" +
            ".025T14 8.775m0 5.5q0-.225.163-.462t.362-.313q.725-.25 1.45-.375T17.5 13q.5 0 .9" +
            "88.063t.962.162q.225.05.388.25t.162.45q0 .425-.275.625t-.7.1q-.35-.075-.737-.112" +
            "T17.5 14.5q-.65 0-1.275.113t-1.2.312q-.45.175-.737-.012T14 14.275m0-2.75q0-.225." +
            "163-.462t.362-.313q.725-.25 1.45-.375t1.525-.125q.5 0 .988.063t.962.162q.225.05." +
            "388.25t.162.45q0 .425-.275.625t-.7.1q-.35-.075-.737-.112t-.788-.038q-.65 0-1.275" +
            ".125t-1.2.325q-.45.175-.737-.025t-.288-.65",
    )

    /** 故事书（历史 emoji 📕）。 */
    val Stories: ImageVector = icon(
        name = "Ledger.Stories",
            "M11.463 19.475q-.263-.075-.488-.2q-1.025-.625-2.15-.95T6.5 18q-1.05 0-2.063.275T" +
            "2.5 19.05q-.525.275-1.012-.025T1 18.15V6.1q0-.275.138-.525T1.55 5.2q1.15-.6 2.4-" +
            ".9T6.5 4q1.45 0 2.838.375T12 5.5v12.1q1.275-.8 2.675-1.2T17.5 16q.9 0 1.763.15T2" +
            "1 16.6v-12q.375.125.738.263t.712.337q.275.125.413.375T23 6.1v12.05q0 .575-.487.8" +
            "75t-1.013.025q-.925-.5-1.937-.775T17.5 18q-1.2 0-2.325.325t-2.15.95q-.225.125-.4" +
            "88.2T12 19.55t-.537-.075m3.362-5.225q-.25.225-.537.088T14 13.875V5.7q0-.1.038-.1" +
            "88t.112-.162l4-4q.25-.25.55-.125t.3.475v8.575q0 .125-.05.213t-.125.162z",
    )

    /** 图书馆（历史 emoji 📗）。 */
    val Library: ImageVector = icon(
        name = "Ledger.Library",
            "M3 17.15V10q0-.8.588-1.35t1.387-.5q1.975.3 3.763 1.163T12 11.55q1.475-1.375 3.26" +
            "3-2.238t3.762-1.162q.8-.05 1.388.5T21 10v7.15q0 .8-.525 1.363t-1.325.612q-1.6.25" +
            "-3.1.825t-2.8 1.525q-.275.225-.587.337t-.663.113t-.663-.112t-.587-.338q-1.3-.95-" +
            "2.8-1.525t-3.1-.825q-.8-.05-1.325-.612T3 17.15m6.175-9.325Q8 6.65 8 5t1.175-2.82" +
            "5T12 1t2.825 1.175T16 5t-1.175 2.825T12 9T9.175 7.825",
    )

    /** 记事（历史 emoji 📘）。 */
    val Notes: ImageVector = icon(
        name = "Ledger.Notes",
            "M6 22q-.825 0-1.412-.587T4 20V4q0-.825.588-1.412T6 2h7.175q.4 0 .763.15t.637.425" +
            "l4.85 4.85q.275.275.425.638t.15.762V20q0 .825-.587 1.413T18 22zm7-14q0 .425.288." +
            "713T14 9h4l-5-5z",
    )

    /** 票据（历史 emoji 📙）。 */
    val Receipt: ImageVector = icon(
        name = "Ledger.Receipt",
            "M6 22q-1.25 0-2.125-.875T3 19v-2q0-.425.288-.712T4 16h2V2.6q0-.175.15-.238t.275." +
            "063l.725.725q.15.15.35.15t.35-.15l.8-.8Q8.8 2.2 9 2.2t.35.15l.8.8q.15.15.35.15t." +
            "35-.15l.8-.8q.15-.15.35-.15t.35.15l.8.8q.15.15.35.15t.35-.15l.8-.8q.15-.15.35-.1" +
            "5t.35.15l.8.8q.15.15.35.15t.35-.15l.8-.8q.15-.15.35-.15t.35.15l.8.8q.15.15.35.15" +
            "t.35-.15l.725-.725q.125-.125.275-.063T21 2.6V19q0 1.25-.875 2.125T18 22zm12-2q.4" +
            "25 0 .713-.288T19 19V5H8v11h8q.425 0 .713.288T17 17v2q0 .425.288.713T18 20M10 7h" +
            "4q.425 0 .713.288T15 8t-.288.713T14 9h-4q-.425 0-.712-.288T9 8t.288-.712T10 7m0 " +
            "3h4q.425 0 .713.288T15 11t-.288.713T14 12h-4q-.425 0-.712-.288T9 11t.288-.712T10" +
            " 10m7-1q-.425 0-.712-.288T16 8t.288-.712T17 7t.713.288T18 8t-.288.713T17 9m0 3q-" +
            ".425 0-.712-.288T16 11t.288-.712T17 10t.713.288T18 11t-.288.713T17 12",
    )

    /** 书堆（历史 emoji 📚）。 */
    val Stack: ImageVector = icon(
        name = "Ledger.Stack",
            "M6.75 22q-1.125 0-1.937-.763T4 19.35V5.4q0-.95.588-1.7t1.537-.95l7.5-1.475q.925-" +
            ".2 1.65.4T16 3.225V15.15q0 .725-.45 1.288t-1.15.687L6.525 18.7q-.225.05-.375.238" +
            "T6 19.35q0 .275.225.463T6.75 20H18V5q0-.425.288-.712T19 4t.713.288T20 5v15q0 .82" +
            "5-.587 1.413T18 22zm1.45-5.65q.35-.075.575-.35T9 15.375V5.45q0-.475-.363-.775t-." +
            "837-.2q-.35.075-.575.35T7 5.45v9.925q0 .475.362.775t.838.2",
    )

    /** 工作（历史 emoji 💼）。 */
    val Work: ImageVector = icon(
        name = "Ledger.Work",
            "M4 21q-.825 0-1.412-.587T2 19V8q0-.825.588-1.412T4 6h4V4q0-.825.588-1.412T10 2h4" +
            "q.825 0 1.413.588T16 4v2h4q.825 0 1.413.588T22 8v11q0 .825-.587 1.413T20 21zm6-1" +
            "5h4V4h-4z",
    )

    /** 储蓄（历史 emoji 💰）。 */
    val Savings: ImageVector = icon(
        name = "Ledger.Savings",
            "M5.925 21q-.575 0-1.112-.4t-.713-.975q-.625-2.1-1.025-3.637t-.638-2.7t-.337-2.06" +
            "3T2 9.5q0-2.3 1.6-3.9T7.5 4h5q.675-.9 1.713-1.45T16.5 2q.625 0 1.063.438T18 3.5q" +
            "0 .15-.038.3t-.087.275q-.1.275-.187.55t-.138.6L19.825 7.5H21q.425 0 .713.288T22 " +
            "8.5v5.25q0 .325-.187.575t-.513.375l-2.125.7l-1.25 4.175q-.2.65-.725 1.038T16 21h" +
            "-2q-.825 0-1.412-.587T12 19h-2q0 .825-.587 1.413T8 21zm10.788-10.288Q17 10.425 1" +
            "7 10t-.288-.712T16 9t-.712.288T15 10t.288.713T16 11t.713-.288M12 9q.425 0 .713-." +
            "288T13 8t-.288-.712T12 7H9q-.425 0-.712.288T8 8t.288.713T9 9z",
    )

    /** 银行（历史 emoji 🏦）。 */
    val Bank: ImageVector = icon(
        name = "Ledger.Bank",
            "M5 16v-5q0-.425.288-.712T6 10t.713.288T7 11v5q0 .425-.288.713T6 17t-.712-.288T5 " +
            "16m6 0v-5q0-.425.288-.712T12 10t.713.288T13 11v5q0 .425-.288.713T12 17t-.712-.28" +
            "8T11 16m-8 5q-.425 0-.712-.288T2 20t.288-.712T3 19h18q.425 0 .713.288T22 20t-.28" +
            "8.713T21 21zm14-5v-5q0-.425.288-.712T18 10t.713.288T19 11v5q0 .425-.288.713T18 1" +
            "7t-.712-.288T17 16m4-8H2.9q-.375 0-.638-.262T2 7.1v-.55q0-.275.138-.475T2.5 5.75" +
            "l8.6-4.3q.425-.2.9-.2t.9.2l8.55 4.275q.275.125.413.375t.137.525V7q0 .425-.287.71" +
            "3T21 8",
    )

    /** 卡包（历史 emoji 💳）。 */
    val Card: ImageVector = icon(
        name = "Ledger.Card",
            "M4 20q-.825 0-1.412-.587T2 18V6q0-.825.588-1.412T4 4h16q.825 0 1.413.588T22 6v12" +
            "q0 .825-.587 1.413T20 20zm0-8h16V8H4z",
    )

    /** 账目统计（历史 emoji 📊）。 */
    val Chart: ImageVector = icon(
        name = "Ledger.Chart",
            "M17 20q-.425 0-.712-.288T16 19v-5q0-.425.288-.712T17 13h2q.425 0 .713.288T20 14v" +
            "5q0 .425-.288.713T19 20zm-6 0q-.425 0-.712-.288T10 19V5q0-.425.288-.712T11 4h2q." +
            "425 0 .713.288T14 5v14q0 .425-.288.713T13 20zm-6 0q-.425 0-.712-.288T4 19v-9q0-." +
            "425.288-.712T5 9h2q.425 0 .713.288T8 10v9q0 .425-.288.713T7 20z",
    )

    /** 消费（历史 emoji 🛒）。 */
    val Shopping: ImageVector = icon(
        name = "Ledger.Shopping",
            "M5.588 21.413Q5 20.825 5 20t.588-1.412T7 18t1.413.588T9 20t-.587 1.413T7 22t-1.4" +
            "12-.587m10 0Q15 20.825 15 20t.588-1.412T17 18t1.413.588T19 20t-.587 1.413T17 22t" +
            "-1.412-.587M5.2 4h14.75q.575 0 .875.513t.025 1.037l-3.55 6.4q-.275.5-.737.775T15" +
            ".55 13H8.1L7 15h11q.425 0 .713.288T19 16t-.288.713T18 17H7q-1.125 0-1.7-.987t-.0" +
            "5-1.963L6.6 11.6L3 4H2q-.425 0-.712-.288T1 3t.288-.712T2 2h1.625q.275 0 .525.15t" +
            ".375.425z",
    )

    /** 家庭（历史 emoji 🏠）。 */
    val Home: ImageVector = icon(
        name = "Ledger.Home",
            "M4 19v-9q0-.475.213-.9t.587-.7l6-4.5q.525-.4 1.2-.4t1.2.4l6 4.5q.375.275.588.7T2" +
            "0 10v9q0 .825-.588 1.413T18 21h-3q-.425 0-.712-.288T14 20v-5q0-.425-.288-.712T13" +
            " 14h-2q-.425 0-.712.288T10 15v5q0 .425-.288.713T9 21H6q-.825 0-1.412-.587T4 19",
    )

    /** 出行（历史 emoji 🚗）。 */
    val Car: ImageVector = icon(
        name = "Ledger.Car",
            "M6 19v.5q0 .625-.437 1.063T4.5 21t-1.062-.437T3 19.5v-7.15q0-.175.025-.35t.075-." +
            "325L4.975 6.35q.2-.6.725-.975T6.875 5h10.25q.65 0 1.175.375t.725.975l1.875 5.325" +
            "q.05.15.075.325t.025.35v7.15q0 .625-.437 1.063T19.5 21t-1.062-.437T18 19.5V19zm-" +
            ".2-9h12.4l-1.05-3H6.85zm1.7 6q.625 0 1.063-.437T9 14.5t-.437-1.062T7.5 13t-1.062" +
            ".438T6 14.5t.438 1.063T7.5 16m9 0q.625 0 1.063-.437T18 14.5t-.437-1.062T16.5 13t" +
            "-1.062.438T15 14.5t.438 1.063T16.5 16",
    )

    /** 2026-10-13 之前账本编辑器给的 16 个 emoji → key（老数据/老备份自动换新图标）。 */
    private val LEGACY_EMOJI: Map<String, String> = mapOf(
        "📒" to BOOK,
        "📓" to NOTEBOOK,
        "📔" to JOURNAL,
        "📕" to STORIES,
        "📗" to LIBRARY,
        "📘" to NOTES,
        "📙" to RECEIPT,
        "📚" to STACK,
        "💼" to WORK,
        "💰" to SAVINGS,
        "🏦" to BANK,
        "💳" to CARD,
        "📊" to CHART,
        "🛒" to SHOPPING,
        "🏠" to HOME,
        "🚗" to CAR,
    )

    private val byKey: Map<String, ImageVector> = mapOf(
        BOOK to Book,
        NOTEBOOK to Notebook,
        JOURNAL to Journal,
        STORIES to Stories,
        LIBRARY to Library,
        NOTES to Notes,
        RECEIPT to Receipt,
        STACK to Stack,
        WORK to Work,
        SAVINGS to Savings,
        BANK to Bank,
        CARD to Card,
        CHART to Chart,
        SHOPPING to Shopping,
        HOME to Home,
        CAR to Car,
    )

    /** 账本图标选择器里的展示顺序。 */
    val pickerKeys: List<String> = listOf(
        BOOK, NOTEBOOK, JOURNAL, STORIES, LIBRARY, NOTES, RECEIPT, STACK, WORK, SAVINGS, BANK, CARD, CHART, SHOPPING, HOME, CAR
    )

    /** key 或历史 emoji → 图标；都不认识时返回 null（调用方原样渲染文本/emoji 兜底）。 */
    fun resolve(raw: String): ImageVector? =
        byKey[raw] ?: LEGACY_EMOJI[raw]?.let { byKey[it] }

    /** 选择器按 key 取图标（[pickerKeys] 里的 key 一定有值）。 */
    fun keyVector(key: String): ImageVector? = byKey[key]

    /** 单测遍历用：全部图标、全部 key、历史 emoji 条数。 */
    internal val all: List<ImageVector> = byKey.values.toList()
    internal val allKeys: List<String> = byKey.keys.toList()
    internal val legacyEmojiCount: Int = LEGACY_EMOJI.size

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()
}

/**
 * 账本图标渲染入口（2026-10-13）。
 *
 * `Ledger.icon` 里有三种可能的值，都要能画出来：
 * 1. [LedgerIcons] 的稳定 key（`"book"` …）→ 矢量图标，用调用方给的 `tint` 着色；
 * 2. 2026-10-13 之前的 emoji（`"📒"` …）→ 同一批矢量（`LEGACY_EMOJI` 映射），**不需要数据迁移**；
 * 3. 用户自己输入过的 emoji 或任何未知字符串 → 原样渲染成文本，绝不显示空白。
 *
 * @param icon DB 里的图标字段。
 * @param tint 着色。
 * @param size 图标边长；emoji 兜底按此换算字号。
 * @param contentDescription 无障碍描述；旁边已有账本名时传 null 避免读屏重复。
 */
@Composable
fun LedgerGlyph(
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val vector = remember(icon) { LedgerIcons.resolve(icon) }
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(size),
        )
    } else {
        Text(
            text = icon.ifBlank { "❔" },
            modifier = modifier,
            fontSize = with(LocalDensity.current) { size.toSp() * 0.9f },
            maxLines = 1,
        )
    }
}
