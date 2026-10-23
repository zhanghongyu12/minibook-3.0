package com.minibook.ui.navigation

import android.net.Uri
import com.minibook.domain.model.DebtDirection

/**
 * 导航目的地路由定义（ARCHITECTURE §12、TASK-018 壳；TASK-021 填充导航图）。
 *
 * 未解锁态：NavGraph 顶层 `isLocked` 门控，只可达 [Lock] 目标（真解密门，可达性即安全性，
 * §9）；解锁后 5 个主页面（底部 NavigationBar）经 [MainScreen] 内嵌嵌套图承载，根图另注册
 * sheets 家族 / Phase 2 真实页 / 账本管理。页面间参数只传 `id` 类（UUID，§12），大数据走
 * 共享 `LedgerProvider`。
 *
 * 说明：TASK-021 已填充真实 `NavHost`（[MiniBookNavHost]）；`AppLocked` 不作为独立路由
 * 存在——锁态复用 [Lock] 目标（语义重叠，§12「未解锁态→只可达 lock 目标」）。
 */
sealed class Destination(val route: String) {

    /** 锁屏（冷启动/重锁，§9；未解锁时唯一可达目标）。TASK-022 已落地真实解锁 UI。 */
    data object Lock : Destination("lock")

    /** 首页仪表板（F-01：本月结余/收支合计/最近交易/FAB「记一笔」）。 */
    data object Home : Destination("home")

    /** 收支记录（F-01：筛选 全部/支出/收入 + 按日期分组列表）。 */
    data object Transactions : Destination("transactions")

    /** 债务管理（F-02：明细视图 + 按对象净额视图）。 */
    data object Debts : Destination("debts")

    /** 分类管理（F-03：内置禁删 + 自定义增删改）。 */
    data object Categories : Destination("categories")

    /** 设置（F-04/05/09/11：安全/外观/数据/账本）。 */
    data object Settings : Destination("settings")

    // —— Phase 2 真实页（P-12~14，TASK-033/036/038 已落地真实页面） ——

    /** 搜索（P-12，F-06：交易/债务组合筛选）。 */
    data object Search : Destination("search")

    /** 报表（P-13，F-07：月/年收支趋势 + 分类占比 + 结余走势）。 */
    data object Reports : Destination("reports")

    /** 预算（P-14，F-08：月度总预算 + 分类预算进度）。 */
    data object Budgets : Destination("budgets")

    /** 账单导入（P-15，F-12）：根图全屏页（不进底部 NavigationBar），顶栏返回 popBackStack。 */
    data object Import : Destination("import")

    /** 邮箱凭据（F-13 多账号）：QQ 邮箱 IMAP 凭据列表/编辑全屏页（根图，同 Import 口径），顶栏返回 popBackStack。 */
    data object MailAccount : Destination("mail_account")

    /** 待确认/未识别短信清单（F-14）：根图全屏页（同 Import 口径），顶栏返回 popBackStack。 */
    data object SmsPending : Destination("sms-pending")

    // —— 账本管理（P-11） ——

    /** 账本管理（P-11：账本列表/切换/新增/重命名/删除）。 */
    data object LedgerManage : Destination("ledger-manage")

    // —— 设置二级页（2026-10-10 改版：设置首页只留分组汇总，逐组进二级页） ——

    /** 设置 · 外观（F-09：暗色模式 + 主题颜色）。 */
    data object SettingsAppearance : Destination("settings-appearance")

    /** 设置 · 安全（F-04/05：应用锁 + 锁定时长 + 数据加密）。 */
    data object SettingsSecurity : Destination("settings-security")

    /** 设置 · 数据（备份导出/导入 + 账单导入入口 + 邮箱凭据）。 */
    data object SettingsData : Destination("settings-data")

    /** 设置 · 短信账单（F-14：开关矩阵 + 白名单 + 权限状态）。 */
    data object SettingsSms : Destination("settings-sms")

    /** 设置 · 云同步（F-15：自动同步开关 + 三模式手动同步 + 预览 / 结果 / 冲突留档）。 */
    data object SettingsCloud : Destination("settings-cloud")

    /** 设置 · 账号（2026-10-12：用户信息 / 编辑资料 / 改口令 / 退出 / 注销；登录注册各自独立页）。 */
    data object SettingsAccount : Destination("settings-account")

    /** 设置 · 登录（2026-10-12 第二轮：独立整页，像 App 的登录页）。 */
    data object SettingsLogin : Destination("settings-login")

    /** 设置 · 注册（2026-10-12 第二轮：独立整页，注册成功即登录）。 */
    data object SettingsRegister : Destination("settings-register")

    /** 设置 · 编辑资料（2026-10-12 第二轮：显示名 / 邮箱 / 个性签名 / 地区）。 */
    data object SettingsProfileEdit : Destination("settings-profile-edit")

    /** 设置 · 隐私政策（TASK-076：`res/raw/privacy_policy.txt` 只读展示）。 */
    data object SettingsPrivacyPolicy : Destination("settings-privacy-policy")

    /** 设置 · 用户协议（TASK-076：`res/raw/terms_of_service.txt` 只读展示）。 */
    data object SettingsTerms : Destination("settings-terms")

    // —— 交易详情（P-07） ——

    /** 交易详情全屏页（P-07）：新增/查看/编辑三合一，替代旧 ModalBottomSheet；
     *  `?id={id}` 可选（null=新增）、`?edit=1` 可选（true=直接进编辑态，配合列表长按「编辑」）。 */
    data object TransactionDetail : Destination("transaction-detail?id={id}&edit={edit}") {
        /** 构造带可选 `id` / `edit` 的导航 route（id 为空且 edit=false 时返回无参 base route，即新增态）。 */
        fun createRoute(id: String? = null, edit: Boolean = false): String {
            val base = route.substringBefore('?')
            val params = buildList {
                id?.let { add("id=$it") }
                if (edit) add("edit=1")
            }
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }

    // —— 债务详情（P-08/P-09） ——

    /** 债务详情全屏页（P-08/P-09）：新增/查看/编辑/还款四态合一，替代旧 ModalBottomSheet。
     *  查询参数：`id`（债务 id，null=新增/还款预填）、`party`（还款预填对方，URL 编码）、
     *  `direction`（还款预填方向，枚举 name 序列化）、`edit`（1=直接进编辑态）；四者均可选。 */
    data object DebtDetail : Destination("debt-detail?id={id}&party={party}&direction={direction}&edit={edit}") {
        /** 构造带可选参数的导航 route：无参返回 base route（新增态）。 */
        fun createRoute(
            debtId: String? = null,
            partyName: String? = null,
            direction: DebtDirection? = null,
            edit: Boolean = false,
        ): String {
            val base = route.substringBefore('?')
            val params = buildList {
                debtId?.let { add("id=$it") }
                partyName?.let { add("party=${Uri.encode(it)}") }
                direction?.let { add("direction=${it.name}") }
                if (edit) add("edit=1")
            }
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }

    // —— 底部弹层（sheets）家族：route 含可选 `{id}` 查询参数（UUID，§12），本期注册占位 ——

    /** 记一笔弹层（F-01/F-02 新增交易）。 */
    data object SheetTransaction : SheetRoute("sheet-transaction")

    /** 债务明细弹层（F-02）。 */
    data object SheetDebt : SheetRoute("sheet-debt")

    /** 还款弹层（F-02）。 */
    data object SheetRepay : SheetRoute("sheet-repay")

    /** 分类选择弹层。 */
    data object SheetCategoryPicker : SheetRoute("sheet-category-picker")

    /** 分类编辑弹层。 */
    data object SheetCategoryEditor : SheetRoute("sheet-category-editor")

    /** 账本弹层（F-11 账本切换）。 */
    data object SheetLedger : SheetRoute("sheet-ledger")
}

/**
 * 底部弹层路由基座（sheets 家族，UI §4 C-05）。
 *
 * 统一携带可选 `id` 查询参数（UUID，§12）：route 模式为 `base?id={id}`，未传 id 时经
 * [createRoute] 省略查询参数（navArgument 可空默认 null，navigation-compose 按默认值将
 * 查询参数视为可选）。TASK-021 注册占位，落地 task 读取 `id` 参数接线。
 */
sealed class SheetRoute(baseRoute: String) : Destination("$baseRoute?id={id}") {

    /** 构造带可选 `id` 的导航 route（id 为空时返回无参 base route）。 */
    fun createRoute(id: String? = null): String {
        val base = route.substringBefore('?')
        return if (id == null) base else "$base?id=$id"
    }
}
