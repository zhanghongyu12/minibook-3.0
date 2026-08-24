package com.minibook.domain.usecase.budget

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.BudgetRepository
import java.time.YearMonth
import javax.inject.Inject

/**
 * 删除分类预算（TASK-037，PRD F-08 / DEC-008），作用于当前账本。
 *
 * **校验**：`categoryId` 非空非纯空白，否则抛 [InvalidInput]（无分类则无从删除）。
 *
 * 仅删分类预算，不影响月度总预算与交易（「已用」只读交易表，DATABASE §10.4）。
 * 分类预算经 [BudgetRepository.clearCategoryBudget] 物理删除（id 不存在则为空操作，
 * 幂等）。记入「当前账本」：经 [LedgerProvider.currentLedgerId] 取（ARCHITECTURE §8.2/§8.5）。
 */
class ClearCategoryBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 删除当前账本 [month]、某分类的分类预算。
     *
     * @param month 目标月份（`java.time.YearMonth`）。
     * @param categoryId 待删除的分类预算所属分类 id（必填，非空非空白）。
     * @throws InvalidInput 分类为空。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(month: YearMonth, categoryId: String) {
        if (categoryId.isBlank()) throw InvalidInput("分类预算必须指定分类")
        val ledgerId = ledgerProvider.currentLedgerId()
        budgetRepository.clearCategoryBudget(ledgerId, month, categoryId)
    }
}
