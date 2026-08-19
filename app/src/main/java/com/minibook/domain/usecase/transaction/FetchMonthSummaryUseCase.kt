package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.MonthSummary
import com.minibook.domain.repository.TransactionRepository
import java.time.YearMonth
import javax.inject.Inject

/**
 * 取月度收支汇总（TASK-013，DP-1 首页「本月结余/收支合计」）。
 *
 * **账本维度**：`ledgerId` 经 [LedgerProvider.currentLedgerId] 取当前账本（ARCHITECTURE
 * §8.1/§8.5），再 [TransactionRepository.monthlySummary] DB 端 SQL 聚合（DATABASE §6.1）；
 * 统计口径按当前账本计算，不混账（§8.3）。
 *
 * 金额口径：`MonthSummary.incomeFen/expenseFen/balanceFen` 均为分（Long），
 * `balanceFen = incomeFen - expenseFen`，由 DB 端聚合给出（DATABASE §6.1）。
 */
class FetchMonthSummaryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 当前账本 [month] 月度汇总。
     *
     * @param month 目标月份（minSdk 26 起 `java.time.YearMonth` 可用）。
     * @throws LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(month: YearMonth): MonthSummary {
        val ledgerId = ledgerProvider.currentLedgerId()
        return transactionRepository.monthlySummary(ledgerId, month)
    }
}
