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
 * 报表·视图粒度（F-07，UI §8.2）。
 *
 * [MONTHLY] = 月视图（按天桶，委托 [TransactionRepository.observeDailyTrend]）；
 * [YEARLY] = 年视图（按月桶，委托 [TransactionRepository.observeMonthlyTrend]）。
 */
enum class Granularity {
    MONTHLY,
    YEARLY,
}

/**
 * 报表·收支趋势（图 1）产物：X 轴时间桶 + 收入/支出/结余三序列（单位分）。
 *
 * [balanceValues] 为**每桶**结余（`incomeFen - expenseFen`，即 `PeriodTrend.balanceFen`），
 * 非累计；累计结余走势由 [ObserveBalanceTrendUseCase] 对同一趋势做整数跑表产出（DATABASE §10.3）。
 */
data class ReportTrend(
    val buckets: List<String>,
    val incomeValues: List<Long>,
    val expenseValues: List<Long>,
    val balanceValues: List<Long>,
) {
    /** 无任何数据：桶空 或 收入/支出序列均空（图 1 空态判定）。 */
    val isEmpty: Boolean
        get() = buckets.isEmpty() || (incomeValues.isEmpty() && expenseValues.isEmpty())
}

/**
 * 观察当前账本收支趋势（TASK-036，PRD F-07 / DEC-011），对齐 UI §8.2 / API §12.2。
 *
 * 按 [Granularity] 委托区间聚合（月视图当月首/末日、年视图当年首/末日，见 [periodStart]/[periodEnd]），
 * 返回 [ReportTrend]（X 桶 + 三序列，图表组件直接消费，Canvas 不承担算术 DEC-011）。
 *
 * **observe 特殊性（同 ObserveTransactions/ObserveCategories，TASK-013/015 方案）**：observe 路径
 * 须响应账本切换，故直注 [LedgerDataStore]，以 `currentLedgerId` 流做 `flatMapLatest`——切账本写后
 * 立即发射新 id，取消旧订阅、重跑区间聚合（ARCHITECTURE §8.3，统计口径不混账）。当前账本未初始化
 * （空串，Bootstrap 前）发空 [ReportTrend] 保活，Bootstrap 写入后自动重订阅恢复。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTrendUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 当前账本 [period] 的收支趋势流；任一交易增删改 / 账本切换自动重算发射。
     *
     * @param period 视图目标：月视图 = 目标月份；年视图 = 目标年份（取其 [YearMonth.year]）。
     * @param granularity 月（天桶）/ 年（月桶）。
     */
    fun observe(period: YearMonth, granularity: Granularity): Flow<ReportTrend> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                // 首启 Bootstrap 前无当前账本：发空趋势保活；Bootstrap 写入后自动重订阅。
                flowOf(ReportTrend(emptyList(), emptyList(), emptyList(), emptyList()))
            } else {
                val start = periodStart(period, granularity)
                val end = periodEnd(period, granularity)
                when (granularity) {
                    Granularity.MONTHLY ->
                        transactionRepository.observeDailyTrend(ledgerId, start, end)
                    Granularity.YEARLY ->
                        transactionRepository.observeMonthlyTrend(ledgerId, start, end)
                }.map { it.toReportTrend() }
            }
        }
}

/** [List<PeriodTrend>] → [ReportTrend]（X 桶 + 收入/支出/结余三序列）。 */
internal fun List<PeriodTrend>.toReportTrend(): ReportTrend = ReportTrend(
    buckets = map { it.bucket },
    incomeValues = map { it.incomeFen },
    expenseValues = map { it.expenseFen },
    balanceValues = map { it.balanceFen },
)

/** 报表区间起点（含）：[Granularity.MONTHLY]=当月首日、[Granularity.YEARLY]=当年首日。 */
internal fun periodStart(period: YearMonth, granularity: Granularity): String =
    when (granularity) {
        Granularity.MONTHLY -> period.atDay(1).toString()
        Granularity.YEARLY -> YearMonth.of(period.year, 1).atDay(1).toString()
    }

/** 报表区间终点（含）：[Granularity.MONTHLY]=当月末日、[Granularity.YEARLY]=当年末日。 */
internal fun periodEnd(period: YearMonth, granularity: Granularity): String =
    when (granularity) {
        Granularity.MONTHLY -> period.atEndOfMonth().toString()
        Granularity.YEARLY -> YearMonth.of(period.year, 12).atEndOfMonth().toString()
    }
