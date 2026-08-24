package com.minibook.domain.usecase.search

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
 * 搜索当前账本交易（TASK-032，PRD F-06 / DEC-013），复用在 TASK-031 补全的
 * [TransactionFilter] 全字段筛选。
 *
 * 返回 `Flow<List<Transaction>>`：转发 [TransactionRepository.observeByLedger]，
 * 冷流、仅订阅时执行（ARCHITECTURE §4.3）；**账本切换自动重订阅**
 * （§8.2 / §8.3）——当前账本变化 → `flatMapLatest` 取消旧订阅、重跑
 * [TransactionRepository.observeByLedger]（搜索口径不混账）。
 *
 * **observe 路径的特殊性（与 NetByPartyUseCase 等取值路径的差异）**：
 * [com.minibook.domain.common.LedgerProvider.currentLedgerId] 是 suspend 单次读、
 * 返回 `String`，无法驱动 Flow 重订阅；故本类沿用既有 observe 范式
 * （ObserveTransactionsUseCase，TASK-013）直接注入 [LedgerDataStore]，用其
 * `currentLedgerId: Flow<String>` 做 `flatMapLatest`。取值路径（Add/GetById 等
 * 一次读）仍一律经 [com.minibook.domain.common.LedgerProvider]（§8.5）。若未来
 * LedgerProvider 补 `observeCurrentLedgerId(): Flow<String>`，可替换此处直注（KDoc 留痕）。
 *
 * 空串（首启 Bootstrap 前）处理：发射空列表而非抛异常，Bootstrap 写入 id 后自动
 * 重订阅恢复数据——observe 场景「保活优先」，与取值路径「空串抛 LedgerNotFound」语义并存。
 *
 * @param filter 搜索条件（type / categoryId / dateFrom / dateTo / remark / amountMin /
 * amountMax 全部可空，null=不过滤；amountMin/amountMax 单位「分」、含端点，API §12.1）。
 */
class SearchTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 当前账本交易搜索结果流（随账本切换自动重订阅）。
     *
     * @param filter 搜索条件；省略时返回账本全量交易（等价于不过滤）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                // 首启 Bootstrap 前空串：空列表保活；id 写入后 flatMapLatest 自动重订阅。
                flowOf(emptyList())
            } else {
                transactionRepository.observeByLedger(ledgerId, filter)
            }
        }
}
