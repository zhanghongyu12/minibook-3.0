package com.minibook.domain.usecase.ledger

import com.minibook.domain.model.Ledger
import com.minibook.domain.repository.LedgerRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 观察全部账本（TASK-012），转发 [LedgerRepository.observeAll]。
 *
 * 账本列表页 / 账本管理（TASK-029）使用；按 `position` 升序返回（含 `isDefault` 标记），
 * 冷流、仅订阅时执行（ARCHITECTURE §4.3 冷 Flow 约定）。
 */
class ObserveLedgersUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
) {

    /** 全部账本流（按 position 升序）。 */
    fun observe(): Flow<List<Ledger>> = ledgerRepository.observeAll()
}
