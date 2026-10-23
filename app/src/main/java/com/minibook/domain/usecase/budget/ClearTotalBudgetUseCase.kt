package com.minibook.domain.usecase.budget

import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.BudgetRepository
import java.time.YearMonth
import javax.inject.Inject

/**
 * 删除月度总预算（2026-10-13，F-15x，用户原话：「预算填写了不能删除或者清空」），
 * 作用于当前账本。
 *
 * 与 [ClearCategoryBudgetUseCase] 同一形状：只删当月 `categoryId IS NULL` 的那一行总预算，
 * 幂等（不存在则为空操作）；不影响分类预算与交易（「已用」只读交易表，DATABASE §10.4）。
 * 清空后当月若无任何分类预算，预算页回到空态引导（重新「设置月度总预算」）。
 *
 * 记入「当前账本」：经 [LedgerProvider.currentLedgerId] 取（ARCHITECTURE §8.2/§8.5）。
 */
class ClearTotalBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 删除当前账本 [month] 的月度总预算。
     *
     * @param month 目标月份（`java.time.YearMonth`）。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(month: YearMonth) {
        val ledgerId = ledgerProvider.currentLedgerId()
        budgetRepository.clearTotalBudget(ledgerId, month)
    }
}
