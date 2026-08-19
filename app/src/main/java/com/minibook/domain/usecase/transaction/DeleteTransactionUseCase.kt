package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.InvalidTransactionInput
import com.minibook.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * 物理删除交易（TASK-013，API §3）。
 *
 * `getById` 校验存在（不存在 → [InvalidTransactionInput]，UI 反馈「该笔已删除」）→
 * [TransactionRepository.deleteById] 物理删除。删除按 `id` 单参（API §3），
 * 账本隔离由数据层 `ledgerId` 维度保证（DATABASE §4.1）。
 */
class DeleteTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {

    /**
     * 物理删除 [id] 交易。
     *
     * @throws InvalidTransactionInput 目标交易不存在。
     */
    suspend operator fun invoke(id: String) {
        if (transactionRepository.getById(id) == null) {
            throw InvalidTransactionInput("交易不存在：$id")
        }
        transactionRepository.deleteById(id)
    }
}
