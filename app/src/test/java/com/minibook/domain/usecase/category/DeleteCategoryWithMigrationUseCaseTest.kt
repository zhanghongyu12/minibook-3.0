package com.minibook.domain.usecase.category

import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.testutil.FakeBudgetRepository
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeTransactionRunner
import com.minibook.testutil.fakeLedgerDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeleteCategoryWithMigrationUseCase 级联清理单测（P1-1）：
 * 迁移交易 + 删源分类后，源分类的预算行须在同一事务内被级联清空；
 * 目标分类不继承源分类预算（deleteByCategoryId 的入参是源分类 id）。
 */
class DeleteCategoryWithMigrationUseCaseTest {

    private suspend fun newUseCase(
        categoryRepo: FakeCategoryRepository,
        budgetRepo: FakeBudgetRepository,
        runner: FakeTransactionRunner,
    ): DeleteCategoryWithMigrationUseCase =
        DeleteCategoryWithMigrationUseCase(
            ledgerProvider = LedgerProvider(fakeLedgerDataStore("ledger-1")),
            categoryRepository = categoryRepo,
            budgetRepository = budgetRepo,
            transactionRunner = runner,
        )

    private fun expenseCategory(id: String, name: String): Category = Category(
        id = id,
        ledgerId = "ledger-1",
        name = name,
        icon = "icon",
        color = "#000000",
        type = CategoryType.EXPENSE,
        isBuiltIn = false,
        order = 1,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )

    @Test
    fun `迁移后删源分类 - 级联清理源分类预算且目标不继承`() = runTest {
        val categoryRepo = FakeCategoryRepository().apply {
            categories = listOf(
                expenseCategory("src", "源分类"),
                expenseCategory("dst", "目标分类"),
            )
            countTransactionsByCategoryResult = 2 // 有引用 → 走迁移路径
        }
        val budgetRepo = FakeBudgetRepository()
        val runner = FakeTransactionRunner()

        newUseCase(categoryRepo, budgetRepo, runner).invoke("src", "dst")

        // 迁移编排发生：源分类 src 的交易重挂到 dst
        assertEquals(Triple("ledger-1", "src", "dst"), categoryRepo.lastMigrationCall)
        // P1-1：级联清的是「源分类」预算；目标分类 dst 不继承
        assertEquals("src", budgetRepo.lastDeletedCategoryId)
        assertTrue("迁移+删除+清预算应在同一事务内", runner.transactionsRun > 0)
    }
}