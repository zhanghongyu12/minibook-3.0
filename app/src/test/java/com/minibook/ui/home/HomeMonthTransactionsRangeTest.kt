package com.minibook.ui.home

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页结余卡点击跳转账单当月明细：日期区间纯函数单测。
 *
 * 覆盖 [monthTransactionsRange]（结余卡点击 → [BillsFilterBus] 当月筛选请求的
 * dateFrom/dateTo/periodLabel 求值）：
 * - 普通月/大月/跨年 12 月首末日；闰年与平年 2 月（28/29 天）边界；
 * - `yyyy-MM-dd` 单月单日补零；
 * - periodLabel 中文月份口径：无前导 0、不带「结余」尾缀。
 */
class HomeMonthTransactionsRangeTest {

    @Test
    fun `2026年8月 - 首末日 08-01 至 08-31 periodLabel 2026年8月`() {
        val range = monthTransactionsRange(YearMonth.of(2026, 8))
        assertEquals("2026-08-01", range.dateFrom)
        assertEquals("2026-08-31", range.dateTo)
        assertEquals("2026年8月", range.periodLabel)
    }

    @Test
    fun `闰年 2 月 - 首末日 02-01 至 02-29`() {
        val range = monthTransactionsRange(YearMonth.of(2028, 2))
        assertEquals("2028-02-01", range.dateFrom)
        assertEquals("2028-02-29", range.dateTo)
        assertEquals("2028年2月", range.periodLabel)
    }

    @Test
    fun `平年 2 月 - 首末日 02-01 至 02-28`() {
        val range = monthTransactionsRange(YearMonth.of(2026, 2))
        assertEquals("2026-02-01", range.dateFrom)
        assertEquals("2026-02-28", range.dateTo)
        assertEquals("2026年2月", range.periodLabel)
    }

    @Test
    fun `跨年 12 月 - 首末日 12-01 至 12-31`() {
        val range = monthTransactionsRange(YearMonth.of(2025, 12))
        assertEquals("2025-12-01", range.dateFrom)
        assertEquals("2025-12-31", range.dateTo)
        assertEquals("2025年12月", range.periodLabel)
    }

    @Test
    fun `单月单日补零 - 2026年1月 首末日 01-01 至 01-31`() {
        val range = monthTransactionsRange(YearMonth.of(2026, 1))
        assertEquals("2026-01-01", range.dateFrom)
        assertEquals("2026-01-31", range.dateTo)
        assertEquals("2026年1月", range.periodLabel)
    }
}
