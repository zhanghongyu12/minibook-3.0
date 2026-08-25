package com.minibook.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.report.CategorySlice
import com.minibook.domain.usecase.report.Granularity
import com.minibook.domain.usecase.report.ObserveCategoryBreakdownUseCase
import com.minibook.domain.usecase.report.ObserveTrendUseCase
import com.minibook.domain.usecase.report.ReportTrend
import com.minibook.domain.usecase.report.periodEnd
import com.minibook.domain.usecase.report.periodStart
import com.minibook.ui.navigation.BillsFilterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 图 1 形态：折线 / 柱状（UI §8.2「收支趋势折线/柱状切换」）。 */
enum class TrendChartKind {
    LINE,
    BAR,
}

/**
 * 报表图三态（图 1/2 各自的 Loading / Empty / Error / Success）。
 *
 * [Empty] 与 [Error] 均为对象态；[Success] 承载具体数据（[ReportTrend] /
 * `List<CategorySlice>`），由泛型 [T] 区分。
 */
sealed interface ChartState<out T> {

    /** 流未首发射 / 加载中：卡片骨架占位（C-05）。 */
    data object Loading : ChartState<Nothing>

    /** 该图无任何数据 → [com.minibook.ui.reports.components.ChartEmptyState]（PRD F-07 异常处理）。 */
    data object Empty : ChartState<Nothing>

    /** 观察流异常（账本未初始化 / 数据库异常）：全局引导 + 重试）。 */
    data class Error(val message: String) : ChartState<Nothing>

    /** 非空数据。 */
    data class Success<T>(val value: T) : ChartState<T>
}

/**
 * 报表页 ViewModel（TASK-036，UI §2 P-13 / §8.2）。
 *
 * 职责：组合报表 UseCase，将各自流折叠为 [ChartState]。页面状态：
 * - 视图切换 [viewType]（月 / 年）与 [period]（`YearMonth` 当前月/年）；
 * - 图 1 形态 [trendKind]（折线 / 柱状）；
 * - 图 2 口径 [categoryType]（收入 / 支出占比切换）。
 *
 * 数据源：
 * - 图 1 收支趋势 = [ObserveTrendUseCase] → [ReportsViewModel.trendState]；
 * - 图 2 分类占比 = [ObserveCategoryBreakdownUseCase] → [ReportsViewModel.categoryState]。
 *
 * 账本维度：UseCase 内部以 `currentLedgerId.flatMapLatest` 重订阅（切账本整页重算、
 * 仅当前账本，ARCHITECTURE §8.3）；VM 只负责把「视图/周期/口径」变化经 `flatMapLatest` 传到各自
 * UseCase，不感知账本。冷流语义与既有 observe 场景一致：`WhileSubscribed(5_000)` 保活，UI 离屏
 * 5 秒自动停订、回页重连刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    observeTrend: ObserveTrendUseCase,
    observeCategoryBreakdown: ObserveCategoryBreakdownUseCase,
) : ViewModel() {

    /** 视图粒度：月 / 年（默认月）。 */
    private val _viewType = MutableStateFlow(Granularity.MONTHLY)
    val viewType: StateFlow<Granularity> = _viewType.asStateFlow()

    /** 当前选择周期（月视图 = 月份、年视图 = 该年），默认当前月/年。 */
    private val _period = MutableStateFlow(YearMonth.now())
    val period: StateFlow<YearMonth> = _period.asStateFlow()

    /** 图 1 形态：折线 / 柱状（默认折线）。 */
    private val _trendKind = MutableStateFlow(TrendChartKind.LINE)
    val trendKind: StateFlow<TrendChartKind> = _trendKind.asStateFlow()

    /** 图 2 占比口径：收入 / 支出（默认支出）。 */
    private val _categoryType = MutableStateFlow(TransactionType.EXPENSE)
    val categoryType: StateFlow<TransactionType> = _categoryType.asStateFlow()

    /** 图 1 收支趋势三态。 */
    val trendState: StateFlow<ChartState<ReportTrend>> =
        combine(_viewType, _period) { view, p -> view to p }
            .flatMapLatest { (view, p) -> observeTrend.observe(p, view) }
            .map { trend ->
                if (trend.isEmpty) ChartState.Empty else ChartState.Success(trend)
            }
            .catch { e -> emitChartError(e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartState.Loading)

    /** 图 2 分类占比三态。 */
    val categoryState: StateFlow<ChartState<List<CategorySlice>>> =
        combine(_categoryType, _viewType, _period) { type, view, p -> Triple(type, view, p) }
            .flatMapLatest { (type, view, p) -> observeCategoryBreakdown.observe(type, p, view) }
            .map { slices ->
                if (slices.isEmpty()) ChartState.Empty else ChartState.Success(slices)
            }
            .catch { e -> emitChartError(e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartState.Loading)

    /** 切换视图粒度：月 / 年。 */
    fun setViewType(type: Granularity) {
        _viewType.value = type
    }

    /**
     * 前/后移动一个周期：月视图 ±1 月、年视图 ±1 年。
     *
     * @param forward true=前进到下一周期；false=回退到上一周期。
     */
    fun shiftPeriod(forward: Boolean) {
        val deltaMonths = when (_viewType.value) {
            Granularity.MONTHLY -> if (forward) 1L else -1L
            Granularity.YEARLY -> if (forward) 12L else -12L
        }
        _period.value = _period.value.plusMonths(deltaMonths)
    }

    /** 回到当前月/年（周期切换器点击标签复位）。 */
    fun resetPeriod() {
        _period.value = YearMonth.now()
    }

    /** 切换图 1 形态：折线 / 柱状。 */
    fun setTrendKind(kind: TrendChartKind) {
        _trendKind.value = kind
    }

    /** 切换图 2 占比口径：收入 / 支出。 */
    fun setCategoryType(type: TransactionType) {
        _categoryType.value = type
    }

    /**
     * 分类占比点击 → 构建账单筛选跳转请求（分类 + 当前口径 + 当前周期/粒度）。
     * 纯计算由顶层 [buildBillsFilterRequest] 承担，本方法仅注入当前 VM 状态。
     */
    fun buildBillsFilterRequest(slice: CategorySlice): BillsFilterRequest =
        buildBillsFilterRequest(slice, _categoryType.value, _period.value, _viewType.value)

    /** 观察流异常折叠为 [ChartState.Error]（账本未初始化走引导文案）；[CancellationException] 透传。 */
    private suspend fun <T> FlowCollector<ChartState<T>>.emitChartError(e: Throwable) {
        if (e is CancellationException) throw e
        val message = if (e is LedgerNotFound) LEDGER_ERROR_MESSAGE else LOAD_ERROR_MESSAGE
        emit(ChartState.Error(message))
    }

    companion object {
        /** 当前账本未初始化文案（对齐 HomeViewModel §8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 未知异常兜底文案。 */
        const val LOAD_ERROR_MESSAGE = "加载失败，请稍后重试"
    }
}

/**
 * 纯函数：分类切片 + 口径/周期/粒度 → 账单筛选跳转请求（报表 → 记账 Tab 联动）。
 *
 * 日期区间对齐报表同口径（[periodStart]/[periodEnd]）：月视图 = 当月首/末日、
 * 年视图 = 当年首/末日；periodLabel 月如「2026年8月」、年如「2026年」。
 */
internal fun buildBillsFilterRequest(
    slice: CategorySlice,
    type: TransactionType,
    period: YearMonth,
    granularity: Granularity,
): BillsFilterRequest = BillsFilterRequest(
    categoryId = slice.categoryId,
    categoryName = slice.category?.name,
    type = type,
    dateFrom = periodStart(period, granularity),
    dateTo = periodEnd(period, granularity),
    periodLabel = when (granularity) {
        Granularity.MONTHLY -> "${period.year}年${period.monthValue}月"
        Granularity.YEARLY -> "${period.year}年"
    },
)
