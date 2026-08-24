package com.minibook.domain.usecase.search

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.repository.DebtRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 搜索当前账本借/还（TASK-032，PRD F-06 / DEC-013），复用扩展后的 [DebtFilter] 全字段筛选。
 *
 * 返回 `Flow<List<Debt>>`：转发 [DebtRepository.observeByLedger]，冷流、仅订阅时执行
 * （ARCHITECTURE §4.3）；**账本切换自动重订阅**（§8.2 / §8.3）——当前账本变化 →
 * `flatMapLatest` 取消旧订阅、重跑 [DebtRepository.observeByLedger]（搜索口径不混账）。
 *
 * **observe 路径的特殊性（与 NetByPartyUseCase 等取值路径的差异）**：
 * [com.minibook.domain.common.LedgerProvider.currentLedgerId] 是 suspend 单次读、
 * 返回 `String`，无法驱动 Flow 重订阅；故本类沿用既有 observe 范式
 * （ObserveDebtsUseCase，TASK-014）直接注入 [LedgerDataStore]，用其
 * `currentLedgerId: Flow<String>` 做 `flatMapLatest`。取值路径（Add/NetByParty 等
 * 一次读）仍一律经 [com.minibook.domain.common.LedgerProvider]（§8.5）。
 *
 * @param filter 搜索条件（kind / direction / partyName 全部可空，null=不过滤）。
 */
class SearchDebtsUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 当前账本债务搜索结果流（随账本切换自动重订阅）。
     *
     * @param filter 搜索条件；省略时返回账本全量借/还（等价于不过滤）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(filter: DebtFilter = DebtFilter()): Flow<List<Debt>> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                // 首启 Bootstrap 前空串：空列表保活；id 写入后 flatMapLatest 自动重订阅。
                flowOf(emptyList())
            } else {
                debtRepository.observeByLedger(ledgerId, filter)
            }
        }
}
