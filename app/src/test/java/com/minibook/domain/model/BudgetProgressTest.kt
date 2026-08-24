package com.minibook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预算超支口径单测（TASK-037 重点，PRD F-08 / DEC-008 / DEC-012）。
 *
 * 纯 domain 逻辑，无 Android/依赖（DATABASE §9 Long 分、无浮点）：
 * - [BudgetProgress.overBudget]：已设总额预算（totalBudgetFen>0）且已用 > 预算；
 * - [BudgetProgress.hasOverBudget]：总额超支或任一分类超支（DEC-012 入口提示）；
 * - [BudgetProgress.remainingFen]：预算 − 已用，可为负（超支）；
 * - [CategoryBudgetProgress.overBudget/remainingFen]：分类口径同总额。
 *
 * 覆盖边界：未设总额预算守卫（总预算 0 但已用>0 不算超支）、已用=预算（非超支，
 * 严格 >）、分类超支触发 hasOverBudget、剩余为负。
 */
class BudgetProgressTest {

    // ---- 未设总额预算（totalBudgetFen = 0）守卫 ----

    @Test
    fun `未设总额预算但当月有支出 - overBudget 为 false（守卫）`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 0L,
            totalUsedFen = 8000L,
            categoryProgress = emptyList(),
        )
        // PRD F-08：超支 = 已用 > 预算；未设预算（0）不算超支，仅展示「未设预算」。
        assertFalse("总预算未设置（0）时 overBudget 必须为 false", progress.overBudget)
        assertFalse("无分类预算时 hasOverBudget 必须为 false", progress.hasOverBudget)
    }

    @Test
    fun `未设总额预算且当月无支出 - overBudget 与 hasOverBudget 均为 false`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 0L,
            totalUsedFen = 0L,
            categoryProgress = emptyList(),
        )
        assertFalse(progress.overBudget)
        assertFalse(progress.hasOverBudget)
    }

    // ---- 已设预算且已用 > 预算 → 超支 ----

    @Test
    fun `已设预算且已用大于预算 - overBudget 为 true`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 13000L,
            categoryProgress = emptyList(),
        )
        assertTrue("已用(13000) 大于 预算(10000) 时 overBudget 必须为 true", progress.overBudget)
        // 总额超支 → hasOverBudget 也为 true
        assertTrue("总额超支时 hasOverBudget 必须为 true", progress.hasOverBudget)
    }

    @Test
    fun `已用刚好等于预算 - 不超支（严格大于）`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 10000L,
            categoryProgress = emptyList(),
        )
        // PRD F-08「超支 = 已用 > 预算」，等于不超支
        assertFalse(progress.overBudget)
        assertFalse(progress.hasOverBudget)
    }

    @Test
    fun `已用小于预算 - 不超支且剩余为正`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 6000L,
            categoryProgress = emptyList(),
        )
        assertFalse(progress.overBudget)
        assertEquals(4000L, progress.remainingFen)
    }

    // ---- remainingFen 可为负（超支） ----

    @Test
    fun `超支时 remainingFen 为负`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 13000L,
            categoryProgress = emptyList(),
        )
        assertEquals(-3000L, progress.remainingFen)
    }

    // ---- 分类超支触发 hasOverBudget ----

    @Test
    fun `任一分类超支 - hasOverBudget 为 true（总额未超支）`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 6000L, // 总额未超支
            categoryProgress = listOf(
                categoryProgress(category("cat-1", "餐饮", order = 1), budgetFen = 5000L, usedFen = 1200L),
                categoryProgress(category("cat-2", "交通", order = 2), budgetFen = 3000L, usedFen = 4000L), // 超支
            ),
        )
        assertFalse("总额未超支", progress.overBudget)
        assertTrue("任一分类超支时 hasOverBudget 必须为 true", progress.hasOverBudget)
    }

    @Test
    fun `全部分类未超支且总额未超支 - hasOverBudget 为 false`() {
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 6000L,
            categoryProgress = listOf(
                categoryProgress(category("cat-1", "餐饮", order = 1), budgetFen = 5000L, usedFen = 1200L),
                categoryProgress(category("cat-2", "交通", order = 2), budgetFen = 3000L, usedFen = 2000L),
            ),
        )
        assertFalse(progress.overBudget)
        assertFalse(progress.hasOverBudget)
    }

    // ---- 分类预算进度（CategoryBudgetProgress） ----

    @Test
    fun `分类已用大于分类预算 - 分类 overBudget 为 true 且剩余为负`() {
        val p = categoryProgress(category("cat-1", "餐饮", order = 1), budgetFen = 3000L, usedFen = 4000L)
        assertTrue(p.overBudget)
        assertEquals(-1000L, p.remainingFen)
    }

    @Test
    fun `分类已用等于分类预算 - 分类不超支`() {
        val p = categoryProgress(category("cat-1", "餐饮", order = 1), budgetFen = 3000L, usedFen = 3000L)
        assertFalse(p.overBudget)
        assertEquals(0L, p.remainingFen)
    }

    // ---- 模型字段透传 ----

    @Test
    fun `BudgetProgress 字段透传`() {
        val cat = category("cat-1", "餐饮", order = 1)
        val progress = BudgetProgress(
            month = "2026-08",
            totalBudgetFen = 10000L,
            totalUsedFen = 6000L,
            categoryProgress = listOf(categoryProgress(cat, budgetFen = 5000L, usedFen = 1200L)),
        )
        assertEquals("2026-08", progress.month)
        assertEquals(10000L, progress.totalBudgetFen)
        assertEquals(6000L, progress.totalUsedFen)
        assertEquals(1, progress.categoryProgress.size)
        assertEquals(cat, progress.categoryProgress[0].category)
        assertEquals(5000L, progress.categoryProgress[0].budgetFen)
        assertEquals(1200L, progress.categoryProgress[0].usedFen)
    }

    // ---- 构造助手 ----

    private fun category(id: String, name: String, order: Int): Category = Category(
        id = id,
        ledgerId = "ledger-1",
        name = name,
        icon = "icon",
        color = "#000000",
        type = CategoryType.EXPENSE,
        isBuiltIn = false,
        order = order,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )

    private fun categoryProgress(category: Category, budgetFen: Long, usedFen: Long): CategoryBudgetProgress =
        CategoryBudgetProgress(category = category, budgetFen = budgetFen, usedFen = usedFen)
}
