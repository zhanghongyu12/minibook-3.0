package com.minibook.domain.usecase.debt

import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.repository.DebtRepository
import com.minibook.util.TimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 编辑一条借/还（TASK-014，PRD F-02），语义对齐 API §3（编辑交易同口径）。
 *
 * 变更范围：`partyName / amount / kind / direction / date / remark` 可改；
 * **`createdAt` 不可改**（审计字段，沿用原值）；**`updatedAt` 刷新**为
 * [TimeFormatter.nowIso]（数据层 update 亦强制刷新，双保险）。`ledgerId` 不可改
 * （债务归属当前账本，UI 无跨账本迁移）。
 *
 * 存在性校验：**DebtRepository 无 `getById`（TASK-009 未收录单查），本类借
 * [DebtRepository.observeByLedger] 冷流 `first()` 读当前账本明细，命中 [Debt.id]
 * 即校验通过并取出原 [Debt.createdAt]**——不感知 Room/SQL（DEC-001），仅经
 * Repository 接口转发。
 */
class UpdateDebtUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
) {

    /**
     * 更新一条借/还。
     *
     * @param debt 目标债务完整对象（含 id 与 ledgerId；id 不存在 → 抛异常）。
     * @throws IllegalArgumentException 目标债务不存在（当前账本视图内未命中）。
     */
    suspend operator fun invoke(debt: Debt) {
        val existing = debtRepository.observeByLedger(debt.ledgerId, DebtFilter())
            .first()
            .firstOrNull { it.id == debt.id }
            ?: throw IllegalArgumentException("债务不存在：${debt.id}")
        debtRepository.update(
            debt.copy(
                createdAt = existing.createdAt, // createdAt 不可改（审计字段）
                updatedAt = TimeFormatter.nowIso(),
            ),
        )
    }
}
