package com.minibook.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 分类矢量图标集（2026-10-12，用户原话：「现在的内置分类这套图标感觉太旧了，能不能设计一批新的」）。
 *
 * 与 [MiniBookIcons]（界面骨架图标）分开：这里是**分类专用**的一套 24 个图标，形状语言统一
 * （Material Symbols Rounded，Apache-2.0 公开路径数据，`addPathNodes` 解析，不手绘 `moveTo/curveTo`），
 * 单色，由调用方用分类自己的颜色 `tint` 着色——emoji 无法着色、各机型字形不一，正是「显旧」的根因。
 *
 * **存储口径**：`Category.icon` 仍是 String 列（`data/local/db/entity/CategoryEntity.kt`），
 * 这里存的是 [FOOD] 之类的稳定 key；**不改 Room schema、不做数据迁移**。
 * 老数据（以及老备份）里存的是 emoji，[resolve] 的 `LEGACY_EMOJI` 表把内置分类的 24 个历史 emoji
 * 映射到同一批图标，因此升级后旧账本自动显示新图标；用户自己手输的 emoji 走兜底原样渲染。
 *
 * 路径解析失败只在首帧抛异常（参照 F-15o：编译绿、单测全绿仍可能运行时崩），
 * 故由 `CategoryIconsTest` 在 JVM 侧逐个解析一遍兜底。
 */
object CategoryIcons {

    // —— 稳定 key：写进 `Category.icon`，一旦发布不得改动 ——
    const val FOOD = "food"
    const val TRANSPORT = "transport"
    const val SHOPPING = "shopping"
    const val ENTERTAINMENT = "entertainment"
    const val HOUSING = "housing"
    const val MEDICAL = "medical"
    const val EDUCATION = "education"
    const val CLOTHING = "clothing"
    const val COMMUNICATION = "communication"
    const val GIFT = "gift"
    const val TRAVEL = "travel"
    const val TAKEOUT = "takeout"
    const val TRANSFER = "transfer"
    const val UTILITIES = "utilities"
    const val PAYMENT = "payment"
    const val REPAIR = "repair"
    const val SALARY = "salary"
    const val BONUS = "bonus"
    const val INVESTMENT = "investment"
    const val PARTTIME = "parttime"
    const val REDPACKET = "redpacket"

    /** 礼金（2026-10-13 起与「红包」分开：两者都画信封会看起来重复）。 */
    const val GIFT_MONEY = "giftmoney"
    const val RECEIVE = "receive"
    const val REFUND = "refund"
    const val CASH = "cash"

    /** 餐饮。 */
    val Food: ImageVector = icon(
        name = "Category.Food",
        "M7 9V3q0-.425.288-.712T8 2t.713.288T9 3v6h1V3q0-.425.288-.712T11 2t.713.288T12 3v6q0 1.4" +
            "-.862 2.45T9 12.85V21q0 .425-.288.713T8 22t-.712-.288T7 21v-8.15q-1.275-.35-2.137-1.4T4 " +
            "9V3q0-.425.288-.712T5 2t.713.288T6 3v6zm10 5h-2q-.425 0-.712-.288T14 13V7q0-1.75 1.288-3" +
            ".375T17.95 2q.45 0 .75.35t.3.825V21q0 .425-.288.713T18 22t-.712-.288T17 21z",
    )

    /** 交通。 */
    val Transport: ImageVector = icon(
        name = "Category.Transport",
        "M6 19v.5q0 .625-.437 1.063T4.5 21t-1.062-.437T3 19.5v-7.15q0-.175.025-.35t.075-.325L4.97" +
            "5 6.35q.2-.6.725-.975T6.875 5h10.25q.65 0 1.175.375t.725.975l1.875 5.325q.05.15.075.325t" +
            ".025.35v7.15q0 .625-.437 1.063T19.5 21t-1.062-.437T18 19.5V19zm-.2-9h12.4l-1.05-3H6.85zm" +
            "1.7 6q.625 0 1.063-.437T9 14.5t-.437-1.062T7.5 13t-1.062.438T6 14.5t.438 1.063T7.5 16m9 " +
            "0q.625 0 1.063-.437T18 14.5t-.437-1.062T16.5 13t-1.062.438T15 14.5t.438 1.063T16.5 16",
    )

    /** 购物。 */
    val Shopping: ImageVector = icon(
        name = "Category.Shopping",
        "M6 22q-.825 0-1.412-.587T4 20V8q0-.825.588-1.412T6 6h2q0-1.65 1.175-2.825T12 2t2.825 1.1" +
            "75T16 6h2q.825 0 1.413.588T20 8v12q0 .825-.587 1.413T18 22zm4-16h4q0-.825-.587-1.412T12 " +
            "4t-1.412.588T10 6m5.713 4.713Q16 10.425 16 10V8h-2v2q0 .425.288.713T15 11t.713-.288m-6 0" +
            "Q10 10.426 10 10V8H8v2q0 .425.288.713T9 11t.713-.288",
    )

    /** 娱乐。 */
    val Entertainment: ImageVector = icon(
        name = "Category.Entertainment",
        "m4 4l1.625 3.25q.175.35.5.55t.7.2q.75 0 1.15-.638t.05-1.312L7 4h2l1.625 3.25q.175.35.5.5" +
            "5t.7.2q.75 0 1.15-.638t.05-1.312L12 4h2l1.625 3.25q.175.35.5.55t.7.2q.75 0 1.15-.638t.05" +
            "-1.312L17 4h3q.825 0 1.413.587T22 6v12q0 .825-.587 1.413T20 20H4q-.825 0-1.412-.587T2 18" +
            "V6q0-.825.588-1.412T4 4",
    )

    /** 住房。 */
    val Housing: ImageVector = icon(
        name = "Category.Housing",
        "M4 19v-9q0-.475.213-.9t.587-.7l6-4.5q.525-.4 1.2-.4t1.2.4l6 4.5q.375.275.588.7T20 10v9q0" +
            " .825-.588 1.413T18 21h-3q-.425 0-.712-.288T14 20v-5q0-.425-.288-.712T13 14h-2q-.425 0-." +
            "712.288T10 15v5q0 .425-.288.713T9 21H6q-.825 0-1.412-.587T4 19",
    )

    /** 医疗。 */
    val Medical: ImageVector = icon(
        name = "Category.Medical",
        "M4 22q-.825 0-1.412-.587T2 20V8q0-.825.588-1.412T4 6h4V4q0-.825.588-1.412T10 2h4q.825 0 " +
            "1.413.588T16 4v2h4q.825 0 1.413.588T22 8v12q0 .825-.587 1.413T20 22zm6-16h4V4h-4zm1 9v2q" +
            "0 .425.288.713T12 18t.713-.288T13 17v-2h2q.425 0 .713-.288T16 14t-.288-.712T15 13h-2v-2q" +
            "0-.425-.288-.712T12 10t-.712.288T11 11v2H9q-.425 0-.712.288T8 14t.288.713T9 15z",
    )

    /** 教育。 */
    val Education: ImageVector = icon(
        name = "Category.Education",
        "M13 17.05q1.1-.525 2.213-.788T17.5 16q.9 0 1.763.15T21 16.6V6.7q-.825-.35-1.713-.525T17." +
            "5 6q-1.175 0-2.325.3T13 7.2zm-1.662 2.338q-.313-.088-.588-.238q-.975-.575-2.05-.862T6.5 " +
            "18q-1.05 0-2.062.275T2.5 19.05q-.525.275-1.012-.025T1 18.15V6.1q0-.275.138-.525T1.55 5.2" +
            "q1.175-.575 2.413-.888T6.5 4q1.45 0 2.838.375T12 5.5q1.275-.75 2.663-1.125T17.5 4q1.3 0 " +
            "2.538.313t2.412.887q.275.125.413.375T23 6.1v12.05q0 .575-.487.875t-1.013.025q-.925-.5-1." +
            "937-.775T17.5 18q-1.125 0-2.2.288t-2.05.862q-.275.15-.587.238t-.663.087t-.663-.088M14 8." +
            "776q0-.225.163-.462T14.525 8q.725-.25 1.45-.375T17.5 7.5q.5 0 .988.063t.962.162q.225.05." +
            "388.25t.162.45q0 .425-.275.625t-.7.1q-.35-.075-.737-.112T17.5 9q-.65 0-1.275.125t-1.2.32" +
            "5q-.45.175-.737-.025T14 8.775m0 5.5q0-.225.163-.462t.362-.313q.725-.25 1.45-.375T17.5 13" +
            "q.5 0 .988.063t.962.162q.225.05.388.25t.162.45q0 .425-.275.625t-.7.1q-.35-.075-.737-.112" +
            "T17.5 14.5q-.65 0-1.275.113t-1.2.312q-.45.175-.737-.012T14 14.275m0-2.75q0-.225.163-.462" +
            "t.362-.313q.725-.25 1.45-.375t1.525-.125q.5 0 .988.063t.962.162q.225.05.388.25t.162.45q0" +
            " .425-.275.625t-.7.1q-.35-.075-.737-.112t-.788-.038q-.65 0-1.275.125t-1.2.325q-.45.175-." +
            "737-.025t-.288-.65",
    )

    /** 服饰。 */
    val Clothing: ImageVector = icon(
        name = "Category.Clothing",
        "M6 18h12l-6-4.5zm4.625-11.1q-.125.275-.363.45t-.562.175q-.425 0-.712-.288T8.7 6.525q0-.1" +
            "25.013-.213t.062-.187q.425-.95 1.3-1.537T12 4q1.45 0 2.475 1.013T15.5 7.475q0 1.175-.687" +
            " 2.1T13 10.85v.9l8.6 6.45q.2.125.3.338T22 19q0 .425-.287.713T21 20H3q-.425 0-.712-.288T2" +
            " 19q0-.25.1-.462t.3-.338l8.6-6.45V10q0-.425.3-.712T12.025 9q.625 0 1.05-.45t.425-1.075t-" +
            ".437-1.05T12 6q-.45 0-.825.238t-.55.662",
    )

    /** 通讯。 */
    val Communication: ImageVector = icon(
        name = "Category.Communication",
        "M19.95 21q-3.125 0-6.175-1.362t-5.55-3.863t-3.862-5.55T3 4.05q0-.45.3-.75t.75-.3H8.1q.35" +
            " 0 .625.238t.325.562l.65 3.5q.05.4-.025.675T9.4 8.45L6.975 10.9q.5.925 1.187 1.787t1.513" +
            " 1.663q.775.775 1.625 1.438T13.1 17l2.35-2.35q.225-.225.588-.337t.712-.063l3.45.7q.35.1." +
            "575.363T21 15.9v4.05q0 .45-.3.75t-.75.3",
    )

    /** 人情。 */
    val Gift: ImageVector = icon(
        name = "Category.Gift",
        "M4 17v2h16v-2zM4 6h2.2q-.125-.225-.162-.475T6 5q0-1.25.875-2.125T9 2q.75 0 1.388.388t1.1" +
            "12.962L12 4l.5-.65q.45-.6 1.1-.975T15 2q1.25 0 2.125.875T18 5q0 .275-.038.525T17.8 6H20q" +
            ".825 0 1.412.588T22 8v11q0 .825-.587 1.413T20 21H4q-.825 0-1.412-.587T2 19V8q0-.825.588-" +
            "1.412T4 6m0 8h16V8h-5.1l1.5 2.05q.25.35.2.738t-.4.637t-.737.188t-.638-.388L12 7.4l-2.825" +
            " 3.825q-.25.325-.638.388t-.737-.188t-.412-.638t.187-.737L9.05 8H4zm5.713-8.287Q10 5.425 " +
            "10 5t-.288-.712T9 4t-.712.288T8 5t.288.713T9 6t.713-.288M15 6q.425 0 .713-.288T16 5t-.28" +
            "8-.712T15 4t-.712.288T14 5t.288.713T15 6",
    )

    /** 旅行。 */
    val Travel: ImageVector = icon(
        name = "Category.Travel",
        "m10 13.8l-6.325 2.525q-.6.25-1.137-.112T2 15.2v-.55q0-.3.138-.575t.387-.45L10 8.4V4q0-.8" +
            "25.588-1.412T12 2t1.413.588T14 4v4.4l7.475 5.225q.25.175.388.45t.137.575v.55q0 .65-.537 " +
            "1.013t-1.138.112L14 13.8v3.6l2.575 1.8q.2.15.313.363t.112.462v.6q0 .5-.413.813t-.912.162" +
            "L12 20.5l-3.675 1.1q-.5.15-.913-.162T7 20.625v-.6q0-.25.113-.462t.312-.363L10 17.4z",
    )

    /** 美团。 */
    val Takeout: ImageVector = icon(
        name = "Category.Takeout",
        "M4.625 10L2.7 8.15q-.3-.275-.288-.7t.288-.7t.7-.275t.7.275l.95.95v-.2q.05-.325.188-.6t.3" +
            "62-.5l2.825-2.825q.275-.275.638-.425T9.825 3h4.35q.4 0 .763.15t.637.425L18.4 6.4q.225.22" +
            "5.363.513t.187.612V7.7l.95-.95q.275-.275.7-.275t.7.275q.3.3.287.713t-.312.687l-1.9 1.85z" +
            "M16.2 20H7.8q-.8 0-1.375-.525T5.8 18.15l-.5-6.6h13.4l-.5 6.6q-.05.8-.625 1.325T16.2 20",
    )

    /** 转账。 */
    val Transfer: ImageVector = icon(
        name = "Category.Transfer",
        "M5.825 16L7.7 17.875q.275.275.275.688t-.275.712q-.3.3-.712.3t-.713-.3L2.7 15.7q-.15-.15-" +
            ".213-.325T2.426 15t.063-.375t.212-.325l3.6-3.6q.3-.3.7-.287t.7.312q.275.3.288.7t-.288.7L" +
            "5.825 14H12q.425 0 .713.288T13 15t-.288.713T12 16zm12.35-6H12q-.425 0-.712-.288T11 9t.28" +
            "8-.712T12 8h6.175L16.3 6.125q-.275-.275-.275-.687t.275-.713q.3-.3.713-.3t.712.3L21.3 8.3" +
            "q.15.15.212.325t.063.375t-.063.375t-.212.325l-3.6 3.6q-.3.3-.7.288t-.7-.313q-.275-.3-.28" +
            "8-.7t.288-.7z",
    )

    /** 缴费。 */
    val Utilities: ImageVector = icon(
        name = "Category.Utilities",
        "M10.588 21.413Q10 20.825 10 20h4q0 .825-.587 1.413T12 22t-1.412-.587M9 19q-.425 0-.712-." +
            "288T8 18t.288-.712T9 17h6q.425 0 .713.288T16 18t-.288.713T15 19zm-.75-3q-1.725-1.025-2.7" +
            "38-2.75T4.5 9.5q0-3.125 2.188-5.312T12 2t5.313 2.188T19.5 9.5q0 2.025-1.012 3.75T15.75 1" +
            "6z",
    )

    /** 付款。 */
    val Payment: ImageVector = icon(
        name = "Category.Payment",
        "M4 20q-.825 0-1.412-.587T2 18V6q0-.825.588-1.412T4 4h16q.825 0 1.413.588T22 6v12q0 .825-" +
            ".587 1.413T20 20zm0-8h16V8H4z",
    )

    /** 其他（支出）。 */
    val Repair: ImageVector = icon(
        name = "Category.Repair",
        "M9 15q-2.5 0-4.25-1.75T3 9q0-.5.075-1t.275-.95q.125-.25.313-.375t.412-.175t.463.013t.437" +
            ".262L7.6 9.4l1.8-1.8l-2.625-2.625q-.2-.2-.262-.437T6.5 4.075t.175-.413t.375-.312q.45-.2." +
            "95-.275T9 3q2.5 0 4.25 1.75T15 9q0 .575-.1 1.088t-.3 1.012l5.05 5q.725.725.725 1.775t-.7" +
            "25 1.775t-1.775.725t-1.775-.75l-5-5.025q-.5.2-1.012.3T9 15",
    )

    /** 工资。 */
    val Salary: ImageVector = icon(
        name = "Category.Salary",
        "M3 20q-.825 0-1.412-.587T1 18V8q0-.425.288-.712T2 7t.713.288T3 8v10h16q.425 0 .713.288T2" +
            "0 19t-.288.713T19 20zm4-4q-.825 0-1.412-.587T5 14V6q0-.825.588-1.412T7 4h14q.825 0 1.413" +
            ".588T23 6v8q0 .825-.587 1.413T21 16zm2-2q0-.825-.587-1.412T7 12v2zm10 0h2v-2q-.825 0-1.4" +
            "12.588T19 14m-5-1q1.25 0 2.125-.875T17 10t-.875-2.125T14 7t-2.125.875T11 10t.875 2.125T1" +
            "4 13M7 8q.825 0 1.413-.587T9 6H7zm14 0V6h-2q0 .825.588 1.413T21 8",
    )

    /** 奖金。 */
    val Bonus: ImageVector = icon(
        name = "Category.Bonus",
        "m12 11.925l-1.7 1.3q-.15.125-.3.013t-.1-.288l.65-2.1l-1.75-1.4q-.125-.125-.075-.287T8.95" +
            " 9h2.15l.65-2.05q.05-.175.25-.175t.25.175L12.9 9h2.125q.175 0 .238.163t-.063.287l-1.775 " +
            "1.4l.65 2.1q.05.175-.1.288t-.3-.013zM12 21l-4.675 1.55q-.5.175-.913-.125t-.412-.8v-6.35q" +
            "-.95-1.05-1.475-2.4T4 10q0-3.35 2.325-5.675T12 2t5.675 2.325T20 10q0 1.525-.525 2.875T18" +
            " 15.275v6.35q0 .5-.413.8t-.912.125zm4.25-6.75Q18 12.5 18 10t-1.75-4.25T12 4T7.75 5.75T6 " +
            "10t1.75 4.25T12 16t4.25-1.75",
    )

    /** 投资。 */
    val Investment: ImageVector = icon(
        name = "Category.Investment",
        "M2.7 17.625q-.3-.3-.288-.712t.288-.688l5.275-5.35Q8.55 10.3 9.4 10.3t1.425.575l2.575 2.6" +
            "l5.2-5.15H17q-.425 0-.712-.288T16 7.326t.288-.712t.712-.288h4q.425 0 .713.288t.287.712v4" +
            "q0 .425-.288.713t-.712.287t-.712-.287t-.288-.713v-1.6L14.825 14.9q-.575.575-1.425.575t-1" +
            ".425-.575L9.4 12.325l-5.3 5.3q-.275.275-.7.275t-.7-.275",
    )

    /** 兼职。 */
    val PartTime: ImageVector = icon(
        name = "Category.PartTime",
        "M4 21q-.825 0-1.412-.587T2 19V8q0-.825.588-1.412T4 6h4V4q0-.825.588-1.412T10 2h4q.825 0 " +
            "1.413.588T16 4v2h4q.825 0 1.413.588T22 8v11q0 .825-.587 1.413T20 21zm6-15h4V4h-4z",
    )

    /** 红包（历史 emoji 🧧 与用户自建「红包」分类；礼金见 [GiftMoney]）。 */
    val RedPacket: ImageVector = icon(
        name = "Category.RedPacket",
        "M4 20v-9q-.825 0-1.412-.587T2 9V7q0-.825.588-1.412T4 5h3.2q-.125-.225-.162-.475T7 4q0-1." +
            "25.875-2.125T10 1q.575 0 1.075.213T12 1.8q.425-.4.925-.6T14 1q1.25 0 2.125.875T17 4q0 .2" +
            "75-.05.513T16.8 5H20q.825 0 1.413.588T22 7v2q0 .825-.587 1.413T20 11v9q0 .825-.587 1.413" +
            "T18 22H6q-.825 0-1.412-.587T4 20m9.288-16.713Q13 3.575 13 4t.288.713T14 5t.713-.288T15 4" +
            "t-.288-.712T14 3t-.712.288M9 4q0 .425.288.713T10 5t.713-.288T11 4t-.288-.712T10 3t-.712." +
            "288T9 4M4 7v2h7V7zm7 13v-9H6v9zm2 0h5v-9h-5zm7-11V7h-7v2z",
    )

    /** 礼金。 */
    val GiftMoney: ImageVector = icon(
        name = "Category.GiftMoney",
            "M13.375 21.825q.275.075.638.063t.612-.113L22 19q0-.85-.6-1.425T20 17h-6.85q-.075 0-.175-.012" +
            "t-.15-.038l-1.475-.525q-.2-.075-.275-.25t-.025-.375q.05-.175.25-.275t.4-.025l1.125.425q.1.05" +
            ".163.063t.187.012H15.8q.475 0 .837-.325t.363-.85q0-.35-.213-.675t-.562-.45L9.3 11.125q-.175-" +
            ".05-.35-.088T8.6 11H7v9.025zM1 20q0 .825.588 1.413T3 22t1.413-.587T5 20v-7q0-.825-.587-1.412" +
            "T3 11t-1.412.588T1 13zm14.263-7.937q-.363-.138-.663-.413l-2.75-2.7q-.775-.75-1.312-1.662T10 " +
            "5.3q0-1.375.963-2.337T13.3 2q.8 0 1.5.338t1.2.912q.5-.575 1.2-.913T18.7 2q1.375 0 2.338.963T" +
            "22 5.3q0 1.075-.525 1.988t-1.3 1.662l-2.775 2.7q-.3.275-.662.413T16 12.2t-.737-.137",
    )

    /** 收款。 */
    val Receive: ImageVector = icon(
        name = "Category.Receive",
        "M5 21q-.825 0-1.412-.587T3 19V5q0-.825.588-1.412T5 3h14q.825 0 1.413.588T21 5v14q0 .825-" +
            ".587 1.413T19 21zm7-5q.8 0 1.475-.413t1.1-1.087q.15-.225.375-.363t.5-.137H19V5H5v9h3.55q" +
            ".275 0 .5.138t.375.362q.425.675 1.1 1.088T12 16m-1-5.85V7q0-.425.288-.712T12 6t.713.288T" +
            "13 7v3.15l.875-.875q.15-.15.338-.225t.375-.062t.375.087t.337.225q.275.3.288.7t-.288.7l-2" +
            ".6 2.6q-.15.15-.325.213t-.375.062t-.375-.062t-.325-.213l-2.6-2.6q-.15-.15-.225-.337T8.4 " +
            "9.988t.075-.363T8.7 9.3q.3-.3.713-.312t.712.287z",
    )

    /** 退款。 */
    val Refund: ImageVector = icon(
        name = "Category.Refund",
        "M8 19q-.425 0-.712-.288T7 18t.288-.712T8 17h6.1q1.575 0 2.738-1T18 13.5T16.838 11T14.1 1" +
            "0H7.8l1.9 1.9q.275.275.275.7t-.275.7t-.7.275t-.7-.275L4.7 9.7q-.15-.15-.213-.325T4.426 9" +
            "t.063-.375T4.7 8.3l3.6-3.6q.275-.275.7-.275t.7.275t.275.7t-.275.7L7.8 8h6.3q2.425 0 4.16" +
            "3 1.575T20 13.5t-1.737 3.925T14.1 19z",
    )

    /** 其他（收入）。 */
    val Cash: ImageVector = icon(
        name = "Category.Cash",
        "M12 22q-2.075 0-3.9-.788t-3.175-2.137T2.788 15.9T2 12t.788-3.9t2.137-3.175T8.1 2.788T12 " +
            "2t3.9.788t3.175 2.137T21.213 8.1T22 12t-.788 3.9t-2.137 3.175t-3.175 2.138T12 22m.588-3." +
            "262q.262-.263.262-.613v-.375q1.25-.225 2.15-.975t.9-2.225q0-1.05-.6-1.925T12.9 11.1q-1.5" +
            "-.5-2.075-.875T10.25 9.2t.463-1.025T12.05 7.8q.5 0 .875.175t.625.475t.563.412t.587-.012q" +
            ".375-.15.513-.513t-.063-.662q-.4-.575-.987-.975T12.9 6.25v-.375q0-.35-.262-.612T12.025 5" +
            "t-.612.263t-.263.612v.375q-1.25.275-1.95 1.1T8.5 9.2q0 1.175.688 1.9t2.162 1.25q1.575.57" +
            "5 2.188 1.025t.612 1.175q0 .825-.587 1.213t-1.413.387q-.65 0-1.175-.312T10.1 14.9q-.2-.3" +
            "5-.525-.475t-.65 0q-.35.125-.513.475t-.012.675q.4.85 1.075 1.388t1.625.737v.425q0 .35.26" +
            "3.613t.612.262t.613-.262",
    )

    /** 旧数据里的 emoji → key（2026-10-12 之前 `BuiltInCategories` 的 24 条历史取值）。 */
    private val LEGACY_EMOJI: Map<String, String> = mapOf(
        "🍜" to FOOD,
        "🚗" to TRANSPORT,
        "🛒" to SHOPPING,
        "🎬" to ENTERTAINMENT,
        "🏠" to HOUSING,
        "💊" to MEDICAL,
        "📚" to EDUCATION,
        "👔" to CLOTHING,
        "📱" to COMMUNICATION,
        "🎁" to GIFT,
        "✈️" to TRAVEL,
        "🥡" to TAKEOUT,
        "💸" to TRANSFER,
        "💡" to UTILITIES,
        "💳" to PAYMENT,
        "🔧" to REPAIR,
        "💰" to SALARY,
        "🎯" to BONUS,
        "📈" to INVESTMENT,
        "💼" to PARTTIME,
        "🧧" to REDPACKET,
        "📥" to RECEIVE,
        "↩️" to REFUND,
        "💵" to CASH,
    )

    private val byKey: Map<String, ImageVector> = mapOf(
        FOOD to Food,
        TRANSPORT to Transport,
        SHOPPING to Shopping,
        ENTERTAINMENT to Entertainment,
        HOUSING to Housing,
        MEDICAL to Medical,
        EDUCATION to Education,
        CLOTHING to Clothing,
        COMMUNICATION to Communication,
        GIFT to Gift,
        TRAVEL to Travel,
        TAKEOUT to Takeout,
        TRANSFER to Transfer,
        UTILITIES to Utilities,
        PAYMENT to Payment,
        REPAIR to Repair,
        SALARY to Salary,
        BONUS to Bonus,
        INVESTMENT to Investment,
        PARTTIME to PartTime,
        GIFT_MONEY to GiftMoney,
        REDPACKET to RedPacket,
        RECEIVE to Receive,
        REFUND to Refund,
        CASH to Cash,
    )

    /** 分类图标选择器里的展示顺序（= 内置模板顺序：支出 16 个在前、收入 8 个在后；[REDPACKET] 附在末尾，仍可给自建「红包」用）。 */
    val pickerKeys: List<String> = listOf(
        FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, HOUSING, MEDICAL, EDUCATION, CLOTHING, COMMUNICATION, GIFT, TRAVEL, TAKEOUT, TRANSFER, UTILITIES, PAYMENT, REPAIR, SALARY, BONUS, INVESTMENT, PARTTIME, GIFT_MONEY, RECEIVE, REFUND, CASH, REDPACKET
    )

    /**
     * key 或历史 emoji → 图标；都不认识时返回 null（调用方原样渲染文本/emoji 兜底）。
     *
     * 2026-10-13（用户反馈「收入的礼金和红包的图标重复了」）：历史账本里内置「礼金」存的是
     * 🧧 / `"redpacket"`，与用户自建「红包」分类（多半也选 🧧 或信封）看起来完全一样。
     * 这里**只对名为「礼金」的分类**把信封图形改道到 [GiftMoney]，不改任何用户数据——
     * 用户自建的「红包」分类继续渲染红信封。
     */
    fun resolve(raw: String, name: String? = null): ImageVector? {
        val vector = byKey[raw] ?: LEGACY_EMOJI[raw]?.let { byKey[it] }
        if (name == GIFT_MONEY_NAME && (vector === RedPacket || vector === Gift)) return GiftMoney
        return vector
    }

    private const val GIFT_MONEY_NAME = "礼金"

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
