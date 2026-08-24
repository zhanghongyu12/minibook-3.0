package com.minibook.domain.usecase.report

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.PeriodTrend
import com.minibook.domain.repository.TransactionRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 报表·累计结余走势（图 3 产物，PRD F-07 / DATABASE §10.3）。
 *
 * 对每日/每月 [PeriodTrend.balanceFen] 做 **domain 层整数跑表**（依次累加，无浮点），
 * 产出结余走势序列；**无新 SQL**（DATABASE §10.3「累计结余 domain 层整数跑表」）。
 */
data class BalanceTrend(
    val buckets: List<String>,
    val cumulativeBalanceValues: List<Long>,
) {
    /** 无任何数据：桶空 或 累计序列空（图 3 空态判定）。 */
    val isEmpty: Boolean
        get() = buckets.isEmpty() || cumulativeBalanceValues.isEmpty()
}

/**
 * 观察当前账本累计结余走势（TASK-036，PRD F-07 / DEC-011），对齐 UI §8.2。
 *
 * 与 [ObserveTrendUseCase] 同口径（月视图按天桶 / 年视图按月桶，区间 [periodStart]/[periodEnd]），
 * 差异仅在下游：本 UseCase 对 `PeriodTrend.balanceFen` 做整数**累计跑表**（`balanceFen` = 收入 − 支出，
 * `PeriodTrend` 计算属性，见 [PeriodTrend.balanceFen]），产出图 3 单系列结余走势。
 *
 * **observe 特殊性（同 ObserveTrendUseCase，TASK-013/015 方案）**：直注 [LedgerDataStore] 以
 * `currentLedgerId` 流做 `flatMapLatest`（切账本重订阅、口径不混账）；当前账本未初始化发空走势保活。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBalanceTrendUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 当前账本 [period] 的累计结余走势流。
     *
     * @param period 视图目标：月视图 = 目标月份；年视图 = 目标年份（取其 [YearMonth.year]）。
     * @param granularity 月（天桶）/ 年（月桶）。
     */
    fun observe(period: YearMonth, granularity: Granularity): Flow<BalanceTrend> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                flowOf(BalanceTrend(emptyList(), emptyList()))
            } else {
                val start = periodStart(period, granularity)
                val end = periodEnd(period, granularity)
                when (granularity) {
                    Granularity.MONTHLY ->
                        transactionRepository.observeDailyTrend(ledgerId, start, end)
                    Granularity.YEARLY ->
                        transactionRepository.observeMonthlyTrend(ledgerId, start, end)
                }.map { it.toBalanceTrend() }
            }
        }
}

/** [List<PeriodTrend>] → 累计结余走势（每桶 `balanceFen` 依次累加）。 */
internal fun List<PeriodTrend>.toBalanceTrend(): BalanceTrend = BalanceTrend(
    buckets = map { it.bucket },
    cumulativeBalanceValues = map { it.balanceFen }
        .runningFold(0L) { acc, value -> acc + value }
        .drop(1),
)
