package com.minibook.domain.usecase.debt

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.repository.DebtRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 删除一条借/还（TASK-014，PRD F-02）——物理删除。
 *
 * 存在性校验：**DebtRepository 无 `getById`（TASK-009 未收录单查），本类借
 * [DebtRepository.observeByLedger] 冷流 `first()` 在**当前账本视图**内校验
 * [Debt.id] 命中**——债务删除场景恒在当前账本明细页发起，故以当前账本为校验域
 * （ARCHITECTURE §8.1 账本维度统一；跨账本 id 视为不存在，防御性拒绝）。
 *
 * 删除不级联（借/还无子记录）；净额随 [NetByPartyUseCase] 实时重算。
 */
class DeleteDebtUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 删除一条借/还。
     *
     * @param id 目标债务 id。
     * @throws InvalidInput 当前账本视图内不存在该债务。
     */
    suspend operator fun invoke(id: String) {
        val ledgerId = ledgerProvider.currentLedgerId()
        val exists = debtRepository.observeByLedger(ledgerId, DebtFilter())
            .first()
            .any { it.id == id }
        if (!exists) throw InvalidInput("债务不存在：$id")
        debtRepository.deleteById(id)
    }
}
