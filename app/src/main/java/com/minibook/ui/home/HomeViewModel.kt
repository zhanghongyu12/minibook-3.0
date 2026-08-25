package com.minibook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Category
import com.minibook.domain.model.Ledger
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.usecase.budget.ObserveBudgetProgressUseCase
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.ledger.ObserveLedgersUseCase
import com.minibook.domain.usecase.transaction.FetchMonthSummaryUseCase
import com.minibook.domain.usecase.transaction.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 首页仪表板 UI 状态（TASK-023，UI §3 三态 + P-02 四态）。
 *
 * - [Loading]：初始加载占位（骨架屏）。
 * - [Empty]：当前账本无任何交易记录（[currentLedger] 非空时顶部账本选择器仍可展示）。
 * - [Error]：当前账本未初始化 / id 在库中不存在（[LedgerNotFound]）或未知异常。
 * - [Success]：月度汇总 + 最近交易（前 10 条，A-3）+ 当前账本。
 */
sealed interface HomeUiState {

    /** 初始加载态。 */
    data object Loading : HomeUiState

    /** 空态：无交易记录；[currentLedger] 供顶部账本选择器展示（结余卡仍显示 ¥0.00）。 */
    data class Empty(val currentLedger: Ledger?) : HomeUiState

    /** 错误态：展示错误文案并引导账本管理（本期无跳转，仅文案）。 */
    data class Error(val message: String) : HomeUiState

    /** 成功态：所选月份汇总 + 最近交易（前 10，A-3）+ 分类 id→[Category] 映射（联表展示用）。 */
    data class Success(
        val balance: Long,
        val income: Long,
        val expense: Long,
        val recentTransactions: List<Transaction>,
        val currentLedger: Ledger?,
        val categoriesById: Map<String, Category>,
        val month: YearMonth,
    ) : HomeUiState
}

/**
 * 首页仪表板 ViewModel（TASK-023，UI §2 P-02，ARCHITECTURE §8.2/§8.3）。
 *
 * 组合数据流（[ObserveTransactionsUseCase.observe] 交易流 + [ObserveLedgersUseCase.observe]
 * 账本流 + [ObserveCategoriesUseCase.observe] 分类流 + [LedgerDataStore.currentLedgerId] 当前账本 id
 * + [refresh] 手动刷新触发器），再经 `flatMapLatest` 每次变化重算月度汇总 [FetchMonthSummaryUseCase]：
 *
 * - **月份切换（本月结余）**：[selectedMonth] 为可观察月份（初值当前月），作为最外层
 *   `flatMapLatest` 驱动：切月后交易观察流以 `TransactionFilter(dateFrom, dateTo)` 重订阅到该月、
 *   [FetchMonthSummaryUseCase] 取该月汇总、[HomeUiState.Success.month] 携带所选月。预算红点
 *   [budgetOverBudget] 直接复用 [selectedMonth]，随所选月份联动（不再由 refresh 单独滚动）。
 * - **分类联表**：分类流经 [ObserveCategoriesUseCase] 与交易流同账本同源（内置切换重订阅），
 *   在 [HomeUiState.Success] 侧折叠为 `categoriesById`（id→[Category]）供 UI 联表展示。
 * - **账本切换自动重订阅**：交易/分类流 UseCase 已内置 `flatMapLatest`（§8.3），账本写后立即重订阅；
 *   本 VM 的 `combine` 随 `currentLedgerId` 变化整体重算，月度汇总随账本同步刷新，不混账（§8.3）。
 * - **当前账本对象解析**：`observeLedgers()` 列表（按 position 升序）中「匹配 currentLedgerId」
 *   优先、否则 `isDefault=true` 兜底；两者皆无（首启 Bootstrap 前）→ [HomeUiState.Error]
 *   引导回账本管理（§8.5）。
 * - **防闪烁**：`stateIn(WhileSubscribed(5000))` 保留 5s 订阅窗口；`flatMapLatest` 重算期间
 *   保持旧值直出，不闪 Loading（UI §2 P-02 边界「切账本整卡瞬时刷新」）。
 *
 * @param fetchMonthSummary 月度汇总（`balanceFen = incomeFen - expenseFen`）。
 * @param observeTransactions 当前账本交易流（按 [selectedMonth] 的 dateFrom/dateTo 限定该月，时间倒序）。
 * @param observeLedgers 全部账本流（按 position 升序，含 isDefault）。
 * @param observeCategories 当前账本分类流（filter=null 全部，用于 id→[Category] 联表）。
 * @param observeBudgetProgress 所选月份预算进度流（用于首页预算入口超支红点，DEC-012）。
 * @param ledgerDataStore 当前账本 id DataStore（用于解析当前账本对象与空串兜底）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fetchMonthSummary: FetchMonthSummaryUseCase,
    private val observeTransactions: ObserveTransactionsUseCase,
    private val observeLedgers: ObserveLedgersUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val observeBudgetProgress: ObserveBudgetProgressUseCase,
    private val ledgerDataStore: LedgerDataStore,
) : ViewModel() {

    /** 所选月份：初始当前月；由 [previousMonth]/[nextMonth] 切换（nextMonth 上限当前月）。 */
    private val _selectedMonth = MutableStateFlow(YearMonth.now())

    /** 对外只读的所选月份（首页结余卡标题 + 左右切换 + 预算红点联动）。 */
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /** 手动刷新触发器（每 +1 触发一次月度汇总重算）。 */
    private val refreshTrigger = MutableStateFlow(0)

    /**
     * 首页 UI 状态流；初始 [HomeUiState.Loading]。
     *
     * 最外层由 [selectedMonth] `flatMapLatest` 驱动：切月即重订阅「该月交易观察流」并重算该月
     * 汇总；内层 combine（交易/账本/分类/当前账本 id/refresh）任一变化再 `flatMapLatest` 重算
     * [FetchMonthSummaryUseCase]。交易观察流以 `dateFrom/dateTo` 限定所选月份（date 为 `yyyy-MM-dd`）。
     */
    val uiState: StateFlow<HomeUiState> = _selectedMonth
        .flatMapLatest { month ->
            combine(
                observeTransactions.observe(
                    TransactionFilter(
                        dateFrom = month.atDay(1).toString(),
                        dateTo = month.atEndOfMonth().toString(),
                    ),
                ),
                observeLedgers.observe(),
                observeCategories.observe(),
                ledgerDataStore.currentLedgerId,
                refreshTrigger,
            ) { transactions, ledgers, categories, currentLedgerId, _ ->
                HomeSource(transactions, ledgers, categories, currentLedgerId)
            }.flatMapLatest { source ->
                flow {
                    val currentLedger = resolveCurrentLedger(source.ledgers, source.currentLedgerId)
                    if (currentLedger == null) {
                        emit(HomeUiState.Error(LEDGER_ERROR_MESSAGE))
                        return@flow
                    }
                    try {
                        val summary = fetchMonthSummary(month)
                        if (source.transactions.isEmpty()) {
                            emit(HomeUiState.Empty(currentLedger))
                        } else {
                            emit(
                                HomeUiState.Success(
                                    balance = summary.balanceFen,
                                    income = summary.incomeFen,
                                    expense = summary.expenseFen,
                                    recentTransactions = source.transactions.take(MAX_RECENT_TRANSACTIONS),
                                    currentLedger = currentLedger,
                                    categoriesById = source.categories.associateBy { it.id },
                                    month = month,
                                )
                            )
                        }
                    } catch (e: LedgerNotFound) {
                        emit(HomeUiState.Error(LEDGER_ERROR_MESSAGE))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emit(HomeUiState.Error("加载失败，请稍后重试"))
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    /**
     * 所选月份预算是否超支（[ObserveBudgetProgressUseCase] → [BudgetProgress.hasOverBudget]）。
     * 仅供首页预算入口叠加 `error` 红点（DEC-012「入口轻量提示」，TASK-038 增强项）。
     *
     * 月份源直接复用 [selectedMonth]：预算红点跟随所选月份联动（切月即 `flatMapLatest`
     * 重订阅该月预算进度），不再由 [refresh] 单独滚动到当前月（月份切换任务裁定）。
     */
    val budgetOverBudget: StateFlow<Boolean> = selectedMonth
        .flatMapLatest { observeBudgetProgress.observe(it).map { p -> p.hasOverBudget } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** 上一月（无下限）：`minusMonths(1)` 后由 [selectedMonth] 驱动整体重算。 */
    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    /** 下一月：上限 [YearMonth.now()]，不可滑到未来（已到当前月则 no-op）。 */
    fun nextMonth() {
        _selectedMonth.value = clampNextMonth(_selectedMonth.value, YearMonth.now())
    }

    /** 手动刷新：重触发 [FetchMonthSummaryUseCase]（所选月结余重算）。 */
    fun refresh() {
        refreshTrigger.value += 1
    }

    companion object {
        /** 首页「最近交易」UI 截断条数（UI §2 P-02、A-3，MVP 不打分页）。 */
        const val MAX_RECENT_TRANSACTIONS = 10

        /** 当前账本未初始化文案（§8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /**
         * nextMonth 上限钳制（纯函数，供单测）：`current < now` 时 +1 月，否则原样
         * （当前月/未来月都不可再前进）。
         */
        fun clampNextMonth(current: YearMonth, now: YearMonth): YearMonth =
            if (current < now) current.plusMonths(1) else current
    }
}

/** 解析当前账本对象：优先匹配 currentLedgerId，其次 isDefault=true 兜底；皆无返回 null。 */
private fun resolveCurrentLedger(ledgers: List<Ledger>, currentLedgerId: String): Ledger? =
    ledgers.firstOrNull { it.id == currentLedgerId }
        ?: ledgers.firstOrNull { it.isDefault }

/** [HomeViewModel] 组合源数据：交易列表 + 账本列表 + 分类列表 + 当前账本 id。 */
private data class HomeSource(
    val transactions: List<Transaction>,
    val ledgers: List<Ledger>,
    val categories: List<Category>,
    val currentLedgerId: String,
)
