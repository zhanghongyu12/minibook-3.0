package com.minibook.domain.usecase.budget

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.BudgetRepository
import com.minibook.util.Validators
import java.time.YearMonth
import javax.inject.Inject

/**
 * 设置月度总预算（TASK-037，PRD F-08 / DEC-008），写入当前账本。
 *
 * **校验（顺序执行，任一失败抛 [InvalidInput]）**：
 * 1. **金额 > 0 且不超上限**：`if (!Validators.validateAmount(amount)) throw InvalidInput`
 *    —— PRD F-08 异常处理「预算金额 ≤ 0 或未选月份拒绝保存」；上限沿用
 *    [Validators.MAX_AMOUNT_FEN]（DATABASE §9.4，与交易金额同口径）。
 * 2. **月份合法**：[month] 为强类型 `java.time.YearMonth`，其构造即保证月份范围合法
 *    （1~12，年份在 `Year` 范围内），无需额外运行期校验；若未来改用 `yyyy-MM` 字符串入参，
 *    须先经 `YearMonth.parse` 校验（同 `ImportAllUseCase.isValidYearMonth`）。
 *
 * **唯一性（DEC-008）**：月度总预算（categoryId=null）在 SQLite 中 `(ledgerId, month, NULL)`
 * 的 NULL 彼此不同，`REPLACE` 不去重，故经 [BudgetRepository.setTotalBudget] 在 Repository
 * 层事务内「先删后插」（`deleteTotal → upsertTotal`）保证唯一——本类只委托，不感知事务细节。
 *
 * 记入「当前账本」：经 [LedgerProvider.currentLedgerId] 取（ARCHITECTURE §8.2/§8.5），
 * 本类不感知 DataStore/Room（DEC-001）。
 */
class SetTotalBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 设置当前账本 [month] 的月度总预算。
     *
     * @param amount 金额（**分**，>0 且 ≤ [Validators.MAX_AMOUNT_FEN]；UI 负责元→分换算）。
     * @param month 目标月份（`java.time.YearMonth`，minSdk 26 起可用）。
     * @throws InvalidInput 金额 ≤ 0 或超上限。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(month: YearMonth, amount: Long) {
        // 金额校验（PRD F-08「≤0 拒绝保存」；上限沿用全局金额口径 DATABASE §9.4）。
        if (!Validators.validateAmount(amount)) {
            throw InvalidInput("预算金额必须大于 0 且不超过 ${Validators.MAX_AMOUNT_FEN} 分")
        }
        val ledgerId = ledgerProvider.currentLedgerId()
        // DEC-008：先删后插在 BudgetRepository 层事务内闭合，本类仅委托（不感知事务）。
        budgetRepository.setTotalBudget(ledgerId, month, amount)
    }
}
