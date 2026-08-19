package com.minibook.domain.usecase.transaction

import com.minibook.domain.model.Transaction
import com.minibook.domain.repository.TransactionRepository
import com.minibook.util.TimeFormatter
import javax.inject.Inject

/**
 * 编辑交易（TASK-013，PRD F-01 / API §3）。
 *
 * 语义（API §3）：`type/amount/categoryId/description/date` 可改，`createdAt` **不可改**
 * ——入参 [Transaction] 由调用方按编辑表单构建，本类取库内原值并强制以原 `createdAt` 覆盖，
 * `updatedAt` 刷新为当前时间（[TimeFormatter.nowIso]）；`id/ledgerId/syncStatus` 一并保留
 * 入参原值。输入校验（金额/分类/备注/日期）在 Add 侧同套口径，本类不再重复校验，仅做
 * 「目标存在」前置校验。
 *
 * 流程：`getById(tx.id)` 取原值 → 不存在抛 `IllegalArgumentException`（API §3「id 不存在
 * 为空操作」由 Repository 兜底，本 UseCase 显式校验以便 UI 反馈「该笔已删除」）→
 * `update(tx.copy(createdAt = existing.createdAt, updatedAt = nowIso()))`。
 */
class UpdateTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {

    /**
     * 编辑 [tx.id] 交易。
     *
     * @param tx 编辑后的交易值对象（id 必须存在；createdAt 以库内原值为准，入参值被覆盖）。
     * @throws IllegalArgumentException 目标交易不存在。
     */
    suspend operator fun invoke(tx: Transaction) {
        val existing = transactionRepository.getById(tx.id)
            ?: throw IllegalArgumentException("交易不存在：${tx.id}")
        transactionRepository.update(
            tx.copy(
                createdAt = existing.createdAt,
                updatedAt = TimeFormatter.nowIso(),
            ),
        )
    }
}
