package com.minibook.ui.reports

import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.report.CategorySlice
import com.minibook.domain.usecase.report.Granularity
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 报表分类占比点击 → 账单筛选跳转请求 构建纯函数单测。
 *
 * 覆盖 [buildBillsFilterRequest]（4 参纯函数）的日期区间与周期文案：
 * - 月视图：dateFrom/dateTo = 当月首/末日，periodLabel 如「2026年8月」；
 * - 年视图：dateFrom/dateTo = 当年首/末日，periodLabel 如「2026年」；
 * - 分类元信息/口径透传 + 分类已删（category=null）时 categoryName 为 null。
 */
class ReportsViewModelTest {

    private val period = YearMonth.of(2026, 8)

    @Test
    fun `月视图日期区间为当月首末日且周期文案为 2026年8月`() {
        val slice = CategorySlice(
            categoryId = "cat-food",
            totalFen = 5000L,
            category = category("cat-food", "餐饮", CategoryType.EXPENSE),
        )

        val req = buildBillsFilterRequest(slice, TransactionType.EXPENSE, period, Granularity.MONTHLY)

        assertEquals("cat-food", req.categoryId)
        assertEquals("餐饮", req.categoryName)
        assertEquals(TransactionType.EXPENSE, req.type)
        assertEquals("2026-08-01", req.dateFrom)
        assertEquals("2026-08-31", req.dateTo)
        assertEquals("2026年8月", req.periodLabel)
    }

    @Test
    fun `年视图日期区间为当年首末日且周期文案为 2026年`() {
        val slice = CategorySlice(categoryId = "cat-salary", totalFen = 10_000L, category = null)

        val req = buildBillsFilterRequest(slice, TransactionType.INCOME, period, Granularity.YEARLY)

        assertEquals(TransactionType.INCOME, req.type)
        assertEquals("cat-salary", req.categoryId)
        assertEquals("2026-01-01", req.dateFrom)
        assertEquals("2026-12-31", req.dateTo)
        assertEquals("2026年", req.periodLabel)
        assertNull("分类已删 categoryName 应为 null", req.categoryName)
    }

    private fun category(id: String, name: String, type: CategoryType): Category = Category(
        id = id,
        ledgerId = "ledger-1",
        name = name,
        icon = "🍜",
        color = "#FF0000",
        type = type,
        isBuiltIn = false,
        order = 1,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )
}