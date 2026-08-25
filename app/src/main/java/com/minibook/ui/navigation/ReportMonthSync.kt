package com.minibook.ui.navigation

import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 首页 → 报表月份联动单例。
 *
 * 首页结余卡滑动到的月份（[com.minibook.ui.home.HomeViewModel.selectedMonth]）在点击「报表」
 * 入口时经 [set] 写入；报表页 [com.minibook.ui.reports.ReportsViewModel] 构造时读 [month]
 * 作为初始 period（单向：仅首页→报表初始值，报表内切月不回写首页）。
 *
 * 保留最近一次首页选中的月份（不清空）：报表 VM 每次经 nav backStack entry 重建时即取该值，
 * 未从首页进入过（值为 null）时由报表侧回退 `YearMonth.now()`。
 */
object ReportMonthSync {

    private val _month = MutableStateFlow<YearMonth?>(null)

    /** 首页最近一次选中的月份；null=尚未从首页联动过。 */
    val month: StateFlow<YearMonth?> = _month.asStateFlow()

    /** 首页「报表」入口点击时写入当前选中月份。 */
    fun set(month: YearMonth) {
        _month.value = month
    }
}