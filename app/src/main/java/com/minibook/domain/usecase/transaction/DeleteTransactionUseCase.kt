package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.InvalidTransactionInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.repository.TransactionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 物理删除交易（TASK-013，API §3）。
 *
 * **账本维度统一（TASK-013 评审 W5）**：经 [LedgerProvider.currentLedgerId] 取当前账本，
 * 在**当前账本视图**内校验存在（`observeByLedger` 快照命中 id，对齐 DeleteDebtUseCase；
 * 跨账本 id 视为不存在，防御性拒绝）→ [TransactionRepository.deleteById] 物理删除。
 * 删除按 `id` 单参（API §3）。
 */
class DeleteTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 物理删除 [id] 交易（当前账本内）。
     *
     * @throws InvalidTransactionInput 当前账本视图内不存在该交易。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(id: String) {
        val ledgerId = ledgerProvider.currentLedgerId()
        val exists = transactionRepository.observeByLedger(ledgerId, TransactionFilter())
            .first()
            .any { it.id == id }
        if (!exists) throw InvalidTransactionInput("交易不存在：$id")
        transactionRepository.deleteById(id)
    }
}
