package com.minibook.ui.home

import com.minibook.domain.model.MonthSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 首页结余卡环比基准的纯函数单测（TASK-081）。
 *
 * 覆盖 [HomeViewModel.previousBalanceOrNull]：上月**完全无记录**时不给基准（null），
 * 避免把「上月 0」当月度基准、把本月任何金额都显示成「较上月 +¥X」（不记错）。
 */
class HomeViewModelTrendTest {

    @Test
    fun `上月收支全为零 - 无基准`() {
        assertNull(HomeViewModel.previousBalanceOrNull(MonthSummary(0L, 0L, 0L)))
    }

    @Test
    fun `上月只有支出 - 基准为负结余`() {
        assertEquals(
            -1234L,
            HomeViewModel.previousBalanceOrNull(MonthSummary(0L, 1234L, -1234L)),
        )
    }

    @Test
    fun `上月收支相抵为零 - 有记录即有基准`() {
        assertEquals(
            0L,
            HomeViewModel.previousBalanceOrNull(MonthSummary(500L, 500L, 0L)),
        )
    }
}
