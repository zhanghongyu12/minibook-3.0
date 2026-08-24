package com.minibook.domain.usecase.budget

import com.minibook.domain.model.Budget
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionType
import com.minibook.testutil.FakeBudgetRepository
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.fakeLedgerDataStore
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ObserveBudgetProgressUseCase 单测（TASK-037 重点，PRD F-08 / UI D-26~D-27）。
 *
 * 覆盖：三源 combine（预算/分类已用/分类元信息）→ [BudgetProgress]；
 * 总额已用 = 各分类 `CategoryAgg.totalFen` 之和（等价 monthSummary.expenseFen）；
 * 已删分类的孤儿预算（分类不在当前月分类流）被跳过；按 `order` 升序；
 * 当前账本未初始化（空串）发零值进度保活。
 *
 * 观察路径直注 [com.minibook.data.local.datastore.LedgerDataStore]（fake），
 * 以 `currentLedgerId` 流做 `flatMapLatest`（切账本重订阅）。
 */
class ObserveBudgetProgressUseCaseTest {

    private val month = YearMonth.of(2026, 8)

    /** 构造一个已设当前账本 id 的 [LedgerDataStore]（suspend：setCurrentLedgerId 走真实 API）。 */
    private suspend fun ledgerWith(id: String): com.minibook.data.local.datastore.LedgerDataStore =
        fakeLedgerDataStore(id)

    // ---- 三源 combine + 总额已用 = 各分类 totalFen 之和 ----

    @Test
    fun `三源 combine 总额已用为各分类 totalFen 之和`() = runTest {
        val ledger = ledgerWith("ledger-1")
        val budgetRepo = FakeBudgetRepository().apply {
            budgets = listOf(
                totalBudget("budget-total", amount = 10000L),
                categoryBudget("budget-cat-1", categoryId = "cat-1", amount = 5000L),
                categoryBudget("budget-cat-2", categoryId = "cat-2", amount = 3000L),
            )
        }
        val txRepo = FakeTransactionRepository().apply {
            categoryAgg = listOf(
                CategoryAgg(categoryId = "cat-1", totalFen = 6000L),
                CategoryAgg(categoryId = "cat-2", totalFen = 2000L),
                CategoryAgg(categoryId = "cat-deleted", totalFen = 1500L), // 无对应预算/分类，仍计入总额已用
            )
        }
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(category("cat-1", "餐饮", order = 1), category("cat-2", "交通", order = 2))
        }

        val progress = ObserveBudgetProgressUseCase(ledger, budgetRepo, txRepo, catRepo)
            .observe(month)
            .first()

        // 总额已用 = 所有分类 totalFen 之和（含无预算分类），与 monthSummary.expenseFen 等价
        assertEquals(9500L, progress.totalUsedFen)
        assertEquals(10000L, progress.totalBudgetFen)
        // 10000 预算、9500 已用 → 未超支；剩余 +500
        assertFalse(progress.overBudget)
        assertEquals(500L, progress.remainingFen)
        // 月份透传
        assertEquals("2026-08", progress.month)
        // 委托账本正确
        assertEquals("ledger-1", budgetRepo.lastObservedLedgerId)
        assertEquals(month, budgetRepo.lastObservedMonth)
        assertEquals("ledger-1", catRepo.lastObservedLedgerId)
        assertEquals(CategoryType.EXPENSE, catRepo.lastObservedType)
    }

    @Test
    fun `分类已用合计超过总额预算 - overBudget 为 true`() = runTest {
        val ledger = ledgerWith("ledger-1")
        val budgetRepo = FakeBudgetRepository().apply {
            budgets = listOf(
                totalBudget("budget-total", amount = 10000L),
                categoryBudget("budget-cat-1", categoryId = "cat-1", amount = 5000L),
            )
        }
        val txRepo = FakeTransactionRepository().apply {
            categoryAgg = listOf(
                CategoryAgg(categoryId = "cat-1", totalFen = 6000L),
                CategoryAgg(categoryId = "cat-2", totalFen = 5000L), // 无预算分类仍计入总额
            )
        }
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(category("cat-1", "餐饮", order = 1))
        }

        val progress = ObserveBudgetProgressUseCase(ledger, budgetRepo, txRepo, catRepo)
            .observe(month)
            .first()

        assertEquals(11000L, progress.totalUsedFen)
        assertTrue("已用 11000 > 预算 10000 应超支", progress.overBudget)
        assertTrue(progress.hasOverBudget)
        assertEquals(-1000L, progress.remainingFen)
    }

    // ---- 已删分类的孤儿预算被跳过 ----

    @Test
    fun `已删分类的孤儿预算被跳过 - 不计入分类进度`() = runTest {
        val ledger = ledgerWith("ledger-1")
        val budgetRepo = FakeBudgetRepository().apply {
            budgets = listOf(
                totalBudget("budget-total", amount = 10000L),
                categoryBudget("budget-deleted", categoryId = "cat-ghost", amount = 4000L), // 分类已删
                categoryBudget("budget-cat-1", categoryId = "cat-1", amount = 5000L),
            )
        }
        val txRepo = FakeTransactionRepository().apply {
            categoryAgg = listOf(CategoryAgg(categoryId = "cat-1", totalFen = 6000L))
        }
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(category("cat-1", "餐饮", order = 1)) // cat-ghost 不在分类流
        }

        val progress = ObserveBudgetProgressUseCase(ledger, budgetRepo, txRepo, catRepo)
            .observe(month)
            .first()

        // 孤儿预算被跳过：仅 cat-1 进入分类进度
        assertEquals(1, progress.categoryProgress.size)
        assertEquals("cat-1", progress.categoryProgress[0].category.id)
        assertEquals(5000L, progress.categoryProgress[0].budgetFen)
        // 总额预算不受孤儿预算影响
        assertEquals(10000L, progress.totalBudgetFen)
        // 总额已用仅统计实际分类聚合
        assertEquals(6000L, progress.totalUsedFen)
    }

    // ---- 按 order 升序 ----

    @Test
    fun `分类进度按 order 升序`() = runTest {
        val ledger = ledgerWith("ledger-1")
        val budgetRepo = FakeBudgetRepository().apply {
            budgets = listOf(
                totalBudget("budget-total", amount = 10000L),
                // 故意乱序插入
                categoryBudget("budget-cat-3", categoryId = "cat-3", amount = 2000L), // order 5
                categoryBudget("budget-cat-1", categoryId = "cat-1", amount = 1000L), // order 1
                categoryBudget("budget-cat-2", categoryId = "cat-2", amount = 1500L), // order 3
            )
        }
        val txRepo = FakeTransactionRepository().apply { categoryAgg = emptyList() }
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(
                category("cat-2", "交通", order = 3),
                category("cat-1", "餐饮", order = 1),
                category("cat-3", "购物", order = 5),
            )
        }

        val progress = ObserveBudgetProgressUseCase(ledger, budgetRepo, txRepo, catRepo)
            .observe(month)
            .first()

        val ids = progress.categoryProgress.map { it.category.id }
        assertEquals(listOf("cat-1", "cat-2", "cat-3"), ids)
    }

    // ---- 当前账本未初始化（空串）→ 零值保活 ----

    @Test
    fun `当前账本未初始化 - 发零值进度保活`() = runTest {
        val ledger = fakeLedgerDataStore() // 空串
        val progress = ObserveBudgetProgressUseCase(
            ledger,
            FakeBudgetRepository(),
            FakeTransactionRepository(),
            FakeCategoryRepository(),
        ).observe(month).first()

        assertEquals("2026-08", progress.month)
        assertEquals(0L, progress.totalBudgetFen)
        assertEquals(0L, progress.totalUsedFen)
        assertEquals(0, progress.categoryProgress.size)
        assertFalse(progress.overBudget)
        assertFalse(progress.hasOverBudget)
    }

    // ---- 构造助手 ----

    private fun totalBudget(id: String, amount: Long): Budget = Budget(
        id = id,
        ledgerId = "ledger-1",
        month = "2026-08",
        categoryId = null,
        amount = amount,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )

    private fun categoryBudget(id: String, categoryId: String, amount: Long): Budget = Budget(
        id = id,
        ledgerId = "ledger-1",
        month = "2026-08",
        categoryId = categoryId,
        amount = amount,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )

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
}
