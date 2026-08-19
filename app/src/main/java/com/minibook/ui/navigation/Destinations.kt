package com.minibook.ui.navigation

/**
 * 导航目的地路由定义（ARCHITECTURE §12、TASK-018 壳；TASK-021 填充导航图）。
 *
 * 未解锁态：NavGraph 顶层 `isLocked` 门控，只可达 [Lock] 目标（真解密门，可达性即安全性，
 * §9）；解锁后 5 个主页面（底部 NavigationBar）经 [MainScreen] 内嵌嵌套图承载，根图另注册
 * sheets 家族 / Phase 2 占位页 / 账本管理。页面间参数只传 `id` 类（UUID，§12），大数据走
 * 共享 `LedgerProvider`。
 *
 * 说明：TASK-021 已填充真实 `NavHost`（[MiniBookNavHost]）；`AppLocked` 不作为独立路由
 * 存在——锁态复用 [Lock] 目标（语义重叠，§12「未解锁态→只可达 lock 目标」）。
 */
sealed class Destination(val route: String) {

    /** 锁屏（冷启动/重锁，§9；未解锁时唯一可达目标）。TASK-022 落地真实解锁 UI，本期占位。 */
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

    // —— Phase 2 占位路由（P-12~14，TASK-021 注册占位，落地 task 替换为真实页面） ——

    /** 搜索（P-12 占位）。 */
    data object Search : Destination("search")

    /** 报表（P-13 占位）。 */
    data object Reports : Destination("reports")

    /** 预算（P-14 占位）。 */
    data object Budgets : Destination("budgets")

    // —— 账本管理（P-11） ——

    /** 账本管理（P-11：账本列表/切换/新增/重命名/删除）。 */
    data object LedgerManage : Destination("ledger-manage")

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
