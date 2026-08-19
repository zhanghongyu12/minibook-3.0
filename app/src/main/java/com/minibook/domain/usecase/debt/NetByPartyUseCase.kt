package com.minibook.domain.usecase.debt

import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.DebtRepository
import javax.inject.Inject

/**
 * 按对方姓名汇总净额（TASK-014，PRD F-02），债务「按对象视图」领域聚合。
 *
 * 返回 `Map<对方姓名, 净额（分）>`：**正值 = 对方欠我，负值 = 我欠对方，0 = 两清**。
 * 口径由 DAO SQL 一次聚合保证（DATABASE §6.2 observeNetByParty）：
 * `lend×owe_me=+`、`lend×i_owe=−`、`repay×owe_me=−`、`repay×i_owe=+`
 * （PRD F-02 逐项对应，见 [AddDebtUseCase] KDoc 四态字典）。
 *
 * **本类不重算净额**（DEC-001 / 单一职责）：仅转发 [DebtRepository.netByParty]，
 * 聚合口径、CASE WHEN 与分组全部锁定在数据层。同一对方多次还款独立成行、净额实时
 * 重算由 DAO 查询天然保证。
 *
 * 当前账本经 [LedgerProvider] 取快照（ARCHITECTURE §8.2 / §8.5）。
 */
class NetByPartyUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 当前账本按对方姓名净额汇总。
     *
     * @return 对方姓名 → 净额（分）；正值=对方欠我，负值=我欠对方，0=两清。
     */
    suspend operator fun invoke(): Map<String, Long> {
        val ledgerId = ledgerProvider.currentLedgerId()
        return debtRepository.netByParty(ledgerId)
    }
}
