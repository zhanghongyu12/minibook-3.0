package com.minibook.domain.usecase.category

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.testutil.FakeBudgetRepository
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeTransactionRunner
import com.minibook.testutil.fakeLedgerDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * DeleteCategoryUseCase 级联清理单测（P1-1）：
 * 删除无关联分类时，其预算行须在同一事务内被 `budgetRepository.deleteByCategoryId`
 * 级联清空，防分类删除后预算成孤儿、从预算进度静默消失。
 */
class DeleteCategoryUseCaseTest {

    private suspend fun newUseCase(
        categoryRepo: FakeCategoryRepository,
        budgetRepo: FakeBudgetRepository,
        runner: FakeTransactionRunner,
    ): DeleteCategoryUseCase =
        DeleteCategoryUseCase(
            ledgerProvider = LedgerProvider(fakeLedgerDataStore("ledger-1")),
            categoryRepository = categoryRepo,
            budgetRepository = budgetRepo,
            transactionRunner = runner,
        )

    private fun expenseCategory(id: String): Category = Category(
        id = id,
        ledgerId = "ledger-1",
        name = "分类$id",
        icon = "icon",
        color = "#000000",
        type = CategoryType.EXPENSE,
        isBuiltIn = false,
        order = 1,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )

    @Test
    fun `删除无关联分类 - 级联清理其预算`() = runTest {
        val categoryRepo = FakeCategoryRepository().apply {
            categories = listOf(expenseCategory("cat-1"))
            countTransactionsByCategoryResult = 0 // 无关联交易 → 直接删路径
        }
        val budgetRepo = FakeBudgetRepository()
        val runner = FakeTransactionRunner()

        newUseCase(categoryRepo, budgetRepo, runner).invoke("cat-1")

        // P1-1：删分类 + 清其预算 两件都在同一事务包装内发生
        assertEquals("cat-1", categoryRepo.lastDeletedId)
        assertEquals("cat-1", budgetRepo.lastDeletedCategoryId)
        assertTrue("级联删预算应发生在事务内", runner.transactionsRun > 0)
    }

    @Test
    fun `有关联交易 - 拒绝删除且不级联清理预算`() = runTest {
        val categoryRepo = FakeCategoryRepository().apply {
            categories = listOf(expenseCategory("cat-1"))
            countTransactionsByCategoryResult = 3 // 有引用 → 应走迁移流程
        }
        val budgetRepo = FakeBudgetRepository()
        val runner = FakeTransactionRunner()

        try {
            newUseCase(categoryRepo, budgetRepo, runner).invoke("cat-1")
            fail("应在守卫阶段抛 InvalidInput")
        } catch (e: InvalidInput) {
            assertTrue(e.message.orEmpty().contains("迁移"))
        }

        // 守卫拒绝：未删分类、也未级联删预算
        assertNull(categoryRepo.lastDeletedId)
        assertNull(budgetRepo.lastDeletedCategoryId)
        assertFalse("守卫路径不应开启事务删除", runner.transactionsRun > 0)
    }
}