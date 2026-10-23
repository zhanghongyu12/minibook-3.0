package com.minibook.domain.usecase.budget

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.testutil.FakeBudgetRepository
import com.minibook.testutil.fakeLedgerDataStore
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * ClearCategoryBudgetUseCase 单测（2026-10-13，F-15w 配套）：
 * 删除分类预算应作用于「当前账本 + 月份 + 分类 id」；空分类 id 在守卫阶段被拒绝。
 */
class ClearCategoryBudgetUseCaseTest {

    @Test
    fun `删除分类预算 - 作用于当前账本月份与分类`() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val useCase = ClearCategoryBudgetUseCase(
            budgetRepository = budgetRepo,
            ledgerProvider = LedgerProvider(fakeLedgerDataStore("ledger-1")),
        )
        val month = YearMonth.of(2026, 10)

        useCase(month, "cat-1")

        assertEquals(
            "应删除当前账本当月指定分类的预算行",
            Triple("ledger-1", month, "cat-1"),
            budgetRepo.lastClearedCategory,
        )
    }

    @Test
    fun `空分类 id - 守卫阶段拒绝且不触达仓库`() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val useCase = ClearCategoryBudgetUseCase(
            budgetRepository = budgetRepo,
            ledgerProvider = LedgerProvider(fakeLedgerDataStore("ledger-1")),
        )

        try {
            useCase(YearMonth.of(2026, 10), "   ")
            fail("应在守卫阶段抛 InvalidInput")
        } catch (e: InvalidInput) {
            assertTrue(e.message.orEmpty().contains("分类"))
        }

        assertEquals("未触达仓库", null, budgetRepo.lastClearedCategory)
    }
}
