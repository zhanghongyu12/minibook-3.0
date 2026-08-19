package com.minibook.domain.usecase.transaction

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.repository.TransactionRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 观察当前账本交易列表（TASK-013，ARCHITECTURE §4.3 / §8.2 / §8.3）。
 *
 * 返回 `Flow<List<Transaction>>`：按 [TransactionFilter] 筛选、时间倒序（DATABASE §6.1
 * observeFiltered，冷流、仅订阅时执行，ARCHITECTURE §4.3）；**账本切换自动重订阅**
 * （§8.3 步骤 2）——`ledgerDataStore.currentLedgerId` 为 Flow，切账本写后立即发射新 id，
 * `flatMapLatest` 取消旧订阅、重跑 [TransactionRepository.observeByLedger]（统计口径不混账）。
 *
 * **observe 路径的特殊性（TASK-013 裁定，与 §8.5 取值路径的差异）**：
 * [com.minibook.domain.common.LedgerProvider.currentLedgerId] 是 suspend 单次读，无法驱动
 * Flow 重订阅；故本 UseCase 在 observe 路径直接注入 [LedgerDataStore]，用其
 * `currentLedgerId: Flow<String>` 做 `flatMapLatest`（任务授权的最小改动）。取值路径
 * （Add/FetchMonthSummary 等一次读）仍一律经 [LedgerProvider]（§8.5）。若未来
 * LedgerProvider 补 `observeCurrentLedgerId(): Flow<String>`，可替换此处直注（KDoc 留痕）。
 *
 * 空串（首启 Bootstrap 前）处理：发射空列表而非抛异常，Bootstrap 写入 id 后自动重订阅
 * 恢复数据——observe 场景「保活优先」，与取值路径「空串抛 LedgerNotFound」语义并存不冲突。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 当前账本交易流。
     *
     * @param filter 筛选条件：全部/支出/收入 + 分类 + 日期区间（API §3）；默认全部。
     */
    fun observe(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                // 首启 Bootstrap 前空串：空列表保活；id 写入后 flatMapLatest 自动重订阅。
                flowOf(emptyList())
            } else {
                transactionRepository.observeByLedger(ledgerId, filter)
            }
        }
}
