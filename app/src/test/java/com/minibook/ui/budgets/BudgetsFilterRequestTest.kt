package com.minibook.ui.budgets

import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionType
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 预算页点击跳转账单 请求构建纯函数单测。
 *
 * 覆盖 [buildBillsFilterRequest]（2 参纯函数）的日期区间与周期文案：
 * - 总额卡（category=null）：categoryId/categoryName 为 null，dateFrom/dateTo = 当月首/末日
 *   `yyyy-MM-dd`，periodLabel 如「2026年8月支出」，type=EXPENSE；
 * - 分类行（category=某分类）：categoryId/categoryName 透传，periodLabel 如「2026年8月·餐饮」；
 * - 跨月（含 2 月闰年月末）区间正确。
 */
class BudgetsFilterRequestTest {

    private val month = YearMonth.of(2026, 8)

    @Test
    fun `总额卡点击_筛当月全部支出_区间与文案`() {
        val req = buildBillsFilterRequest(month, category = null)

        assertNull("总额卡不应按分类筛", req.categoryId)
        assertNull("总额卡不应带分类名", req.categoryName)
        assertEquals(TransactionType.EXPENSE, req.type)
        assertEquals("2026-08-01", req.dateFrom)
        assertEquals("2026-08-31", req.dateTo)
        assertEquals("2026年8月支出", req.periodLabel)
    }

    @Test
    fun `分类行点击_筛该分类当月支出_区间与文案`() {
        val req = buildBillsFilterRequest(month, category("cat-food", "餐饮"))

        assertEquals("cat-food", req.categoryId)
        assertEquals("餐饮", req.categoryName)
        assertEquals(TransactionType.EXPENSE, req.type)
        assertEquals("2026-08-01", req.dateFrom)
        assertEquals("2026-08-31", req.dateTo)
        assertEquals("2026年8月·餐饮", req.periodLabel)
    }

    @Test
    fun `跨月切换_取该月首末日区间_闰年二月月末`() {
        val req = buildBillsFilterRequest(YearMonth.of(2028, 2), category("cat-transport", "交通"))

        assertEquals("2028-02-01", req.dateFrom)
        assertEquals("2028-02-29", req.dateTo)
        assertEquals("2028年2月·交通", req.periodLabel)
    }

    private fun category(id: String, name: String): Category = Category(
        id = id,
        ledgerId = "ledger-1",
        name = name,
        icon = "🍜",
        color = "#FF0000",
        type = CategoryType.EXPENSE,
        isBuiltIn = false,
        order = 1,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )
}
