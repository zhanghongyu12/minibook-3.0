package com.minibook.ui.home

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HomeViewModel 月份切换纯函数单测（首页结余卡月份切换任务）。
 *
 * 覆盖 [HomeViewModel.clampNextMonth]（nextMonth 上限钳制，抽为纯函数供确定性测试）：
 * 上一月可 +1；当前月 / 未来月均不可再前进；跨年 12→1 月边界由 YearMonth.plusMonths 天然处理。
 *
 * 说明：previousMonth 无下限（直接 `minusMonths(1)`，无条件逻辑），不需钳制函数与单测；
 * selectedMonth 驱动 uiState 的完整流式行为依赖较重（6 依赖 VM），按任务约定以纯函数边界测试收口。
 */
class HomeViewModelMonthSwitchTest {

    private val now = YearMonth.of(2026, 8)

    @Test
    fun `上一月 - clampNextMonth 前进一个月`() {
        assertEquals(
            YearMonth.of(2026, 7),
            HomeViewModel.clampNextMonth(YearMonth.of(2026, 6), now),
        )
    }

    @Test
    fun `当前月 - clampNextMonth 不再前进`() {
        assertEquals(now, HomeViewModel.clampNextMonth(now, now))
    }

    @Test
    fun `未来月 - clampNextMonth 防御性原样返回`() {
        assertEquals(
            YearMonth.of(2026, 9),
            HomeViewModel.clampNextMonth(YearMonth.of(2026, 9), now),
        )
    }

    @Test
    fun `跨年 - 12 月前进到次年 1 月且不越过当前月`() {
        val janNow = YearMonth.of(2026, 1)
        assertEquals(
            YearMonth.of(2026, 1),
            HomeViewModel.clampNextMonth(YearMonth.of(2025, 12), janNow),
        )
    }
}
