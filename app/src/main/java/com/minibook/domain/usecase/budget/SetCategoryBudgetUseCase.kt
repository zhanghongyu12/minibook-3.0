package com.minibook.domain.usecase.budget

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.BudgetRepository
import com.minibook.util.Validators
import java.time.YearMonth
import javax.inject.Inject

/**
 * 设置分类预算（TASK-037，PRD F-08 / DEC-008），写入当前账本。
 *
 * **校验（顺序执行，任一失败抛 [InvalidInput]）**：
 * 1. **金额 > 0 且不超上限**：`if (!Validators.validateAmount(amount)) throw InvalidInput`
 *    —— PRD F-08 异常处理「预算金额 ≤ 0 或未选月份拒绝保存」；上限沿用
 *    [Validators.MAX_AMOUNT_FEN]（DATABASE §9.4，与交易金额同口径）。
 * 2. **分类必填**：`categoryId` 非空非纯空白，否则拒绝（分类预算必须以某个分类为锚）。
 * 3. **月份合法**：[month] 为强类型 `java.time.YearMonth`，构造即保证月份范围合法，
 *    无需额外运行期校验（同 [SetTotalBudgetUseCase]）。
 *
 * **唯一性（DEC-008）**：分类预算由 DB `UNIQUE(ledgerId, month, categoryId)` 约束直接保证
 * （upsert / REPLACE 冲突策略），本类只委托 [BudgetRepository.setCategoryBudget]，不感知细节。
 *
 * 记入「当前账本」：经 [LedgerProvider.currentLedgerId] 取（ARCHITECTURE §8.2/§8.5），
 * 本类不感知 DataStore/Room（DEC-001）。
 */
class SetCategoryBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 设置当前账本 [month]、某分类的分类预算（upsert，唯一由 UNIQUE 约束保证）。
     *
     * @param amount 金额（**分**，>0 且 ≤ [Validators.MAX_AMOUNT_FEN]；UI 负责元→分换算）。
     * @param month 目标月份（`java.time.YearMonth`）。
     * @param categoryId 预算所属分类 id（必填，非空非空白）。
     * @throws InvalidInput 金额 ≤ 0 或超上限 / 分类为空。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(month: YearMonth, categoryId: String, amount: Long) {
        if (!Validators.validateAmount(amount)) {
            throw InvalidInput("分类预算金额必须大于 0 且不超过 ${Validators.MAX_AMOUNT_FEN} 分")
        }
        if (categoryId.isBlank()) throw InvalidInput("分类预算必须指定分类")
        val ledgerId = ledgerProvider.currentLedgerId()
        // DEC-008：分类预算 UNIQUE(ledgerId, month, categoryId) 约束直接保证，upsert 即可。
        budgetRepository.setCategoryBudget(ledgerId, month, categoryId, amount)
    }
}
