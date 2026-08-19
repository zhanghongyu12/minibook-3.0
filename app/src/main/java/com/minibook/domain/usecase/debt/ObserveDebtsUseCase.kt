package com.minibook.domain.usecase.debt

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.repository.DebtRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 债务明细流（TASK-014，PRD F-02 / ARCHITECTURE §4.3、§8.2），账本维度 + 可选筛选。
 *
 * 转发 [DebtRepository.observeByLedger]：账本内按时间倒序；[DebtFilter] 仅展开
 * `kind / direction` 两个可空字段（A-2 确认），null = 不过滤（partyName 模糊筛
 * 不入 MVP，净额聚合见 [NetByPartyUseCase]）。
 *
 * **账本切换重订阅（observe 特殊性，ARCHITECTURE §8.3）**：本类是观察路径，不能像
 * 一次性查询那样取当前账本快照，而是订阅 [LedgerDataStore.currentLedgerId] 流并用
 * `flatMapLatest` 在账本切换时**自动重订阅**到新账本——当前账本变化 → 内层 Flow 切换、
 * 取消旧订阅，首页/债务页同步切换不混账。写入路径（Add/Update/Delete/Net）经
 * [LedgerProvider] 取当前账本快照；此处直注 [LedgerDataStore] 取响应式流（与 TASK-013
 * ObserveTransactionsUseCase 同方案）。
 *
 * 冷流、仅订阅时执行（§4.3）。首启 Bootstrap 写入前 `currentLedgerId` 为空串，
 * 内层查询 `ledgerId=""` → 空列表（无债务可记，属正常过渡态）。
 */
class ObserveDebtsUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 观察当前账本债务明细流。
     *
     * @param filter 可选筛选（kind/direction，null=不过滤）；省略时返回账本全量明细。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(filter: DebtFilter = DebtFilter()): Flow<List<Debt>> =
        ledgerDataStore.currentLedgerId
            .flatMapLatest { ledgerId -> debtRepository.observeByLedger(ledgerId, filter) }
}
