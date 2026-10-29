package com.minibook.domain.usecase.debt

import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.repository.DebtRepository
import com.minibook.domain.repository.TransactionRepository
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
 *
 * **账单转债务的解除（DEC-056）**：若该债务是由某条账单转来的，删除时必须把账单侧的
 * `debtId` 清回 NULL——否则那条账单会永远「已转债务」且永远不进统计，成为幽灵行。
 * 两步（删债务 + 清关联）在同一事务内闭合：要么都生效，要么都不生效。
 */
class DeleteDebtUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val ledgerProvider: LedgerProvider,
    private val transactionRepository: TransactionRepository,
    private val transactionRunner: TransactionRunner,
) {

    /**
     * 删除一条借/还，并把由它转来的账单解除关联（统计随即重新计入该账单）。
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
        transactionRunner.runInTransaction {
            debtRepository.deleteById(id)
            // 0 或 1 行：非账单转入的债务不会有匹配行，属正常。
            transactionRepository.clearDebtIdByDebt(ledgerId, id)
        }
    }
}
