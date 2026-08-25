package com.minibook.domain.usecase.transaction

import com.minibook.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * 批量删除交易（记账页批量管理模式）。
 *
 * 纯透传 [TransactionRepository.deleteByIds]（Repository 侧已单事务闭合，本类不承载事务
 * 细节，DEC-001）；唯一业务守卫生 [invoke] 内：**空列表直接返回 0、不触库**（避免空 `IN ()`
 * SQL 或无谓事务）。其余情况透传 ids、返回实际删除行数供 UI 判断。
 */
class DeleteTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {

    /**
     * 批量删除 [ids] 指定交易。
     *
     * @param ids 待删除交易 id 集合；空列表返回 0（不触库）。
     * @return 实际删除行数。
     */
    suspend operator fun invoke(ids: List<String>): Int =
        if (ids.isEmpty()) 0 else transactionRepository.deleteByIds(ids)
}
