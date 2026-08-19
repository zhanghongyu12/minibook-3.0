package com.minibook.ui.navigation

/**
 * 导航目的地路由定义（ARCHITECTURE §12、TASK-018 壳；TASK-021 填充导航图）。
 *
 * 未解锁态：NavGraph 顶层 `isLocked` 门控，只可达 [Lock] 目标（真解密门，可达性即安全性，
 * §9）；解锁后 5 个主页面（底部 NavigationBar）。页面间参数只传 `id` 类（UUID，§12），
 * 大数据走共享 `LedgerProvider`。
 */
sealed class Destination(val route: String) {

    /** 锁屏（冷启动/重锁，§9；未解锁时唯一可达目标）。 */
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
}
