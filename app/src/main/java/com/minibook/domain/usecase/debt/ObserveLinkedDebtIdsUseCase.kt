package com.minibook.domain.usecase.debt

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.repository.TransactionRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 「由账单转来的债务」id 集合流（DEC-056），供债务列表渲染「账单转入」标记。
 *
 * 关联只存在账单侧（`transactions.debtId`，单一事实来源），债务行本身不含来源字段，
 * 所以标记是**反查**出来的：本用例转发
 * [TransactionRepository.observeLinkedDebtIds]，把「已关联的债务 id」摊成集合由 UI 判定。
 *
 * **账本切换重订阅**（ARCHITECTURE §8.3，与 [ObserveDebtsUseCase] 同方案）：订阅
 * [LedgerDataStore.currentLedgerId] 流并 `flatMapLatest`，切账本自动重订阅，不串账。
 *
 * 冷流、仅订阅时执行；首启 Bootstrap 前 `ledgerId=""` → 空集合（正常过渡态）。
 */
class ObserveLinkedDebtIdsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /** 当前账本内「已转债务」的债务 id 集合（无关联时为空集）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<Set<String>> =
        ledgerDataStore.currentLedgerId
            .flatMapLatest { ledgerId -> transactionRepository.observeLinkedDebtIds(ledgerId) }
}
