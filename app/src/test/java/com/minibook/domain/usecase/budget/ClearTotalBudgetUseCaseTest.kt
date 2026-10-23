package com.minibook.domain.usecase.budget

import com.minibook.domain.common.LedgerProvider
import com.minibook.testutil.FakeBudgetRepository
import com.minibook.testutil.fakeLedgerDataStore
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ClearTotalBudgetUseCase 单测（2026-10-13，F-15x；用户原话「预算填写了不能删除或者清空」）：
 * 删除当月总预算应作用于「当前账本 + 传入月份」，且只删总额行（fake 记录调用参数即可验证）。
 */
class ClearTotalBudgetUseCaseTest {

    @Test
    fun `删除月度总预算 - 作用于当前账本与指定月份`() = runTest {
        val budgetRepo = FakeBudgetRepository()
        val useCase = ClearTotalBudgetUseCase(
            budgetRepository = budgetRepo,
            ledgerProvider = LedgerProvider(fakeLedgerDataStore("ledger-1")),
        )
        val month = YearMonth.of(2026, 10)

        useCase(month)

        assertEquals("应清空当前账本的当月总额预算", "ledger-1" to month, budgetRepo.lastClearedTotal)
    }
}
