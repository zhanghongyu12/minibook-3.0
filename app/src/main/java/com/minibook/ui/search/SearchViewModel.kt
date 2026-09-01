package com.minibook.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Category
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.search.SearchDebtsUseCase
import com.minibook.domain.usecase.search.SearchTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 搜索页 ViewModel（TASK-033，UI §8.1 P-12 / F-06 / DEC-013）。
 *
 * 职责：
 * - 持有全文检索条件两组（交易：关键词/金额区间/分类/日期区间/类型；债务：姓名关键词/方向），
 *   条件经 `MutableStateFlow` 暴露、`debounce`(300ms) 仅作用于关键词类字段，其余字段即时；
 * - 条件组合经 `flatMapLatest` 重订阅 [SearchTransactionsUseCase] / [SearchDebtsUseCase]
 *   （二者内置「当前账本自动重订阅」，UI 层不感知账本；范围限当前账本）；
 * - [amountMinFen]/[amountMaxFen] 为「分」Long（元↔分经 [com.minibook.ui.components.MoneyInputField]
 *   内部 [com.minibook.util.MoneyFormatter]），`min > max` 视为**无效**——
 *   金额区间无效时本次查询不执行，UI 折为 [SearchUiState.Error]（文案「最小金额不能大于最大金额」）；
 * - 交易 / 债务两组结果合并为 [SearchUiState] 四态（Loading/Empty/Error/Success），
 *   Success 携带 `transactions` + `debts` 两组 + 分类 id→[Category] 映射（联表展示用，
 *   对齐 TransactionsViewModel TASK-024）。
 *
 * 冷流语义：两个 UseCase 的 `observe` 与分类流均为冷流（仅订阅时执行），故以
 * `WhileSubscribed(5_000)` 保活——UI 离屏 5 秒后自动停止订阅（避免后台常驻查询），
 * 回页即重连刷新（对齐 TransactionsViewModel / ObserveCategoriesUseCase 既有范式）。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTransactions: SearchTransactionsUseCase,
    private val searchDebts: SearchDebtsUseCase,
    observeCategories: ObserveCategoriesUseCase,
) : ViewModel() {

    // ==== 交易组条件 ====
    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    /** 金额（分）下限；null=未输入（不过滤）。 */
    private val _amountMinFen = MutableStateFlow<Long?>(null)
    val amountMinFen: StateFlow<Long?> = _amountMinFen.asStateFlow()

    /** 金额（分）上限；null=未输入（不过滤）。 */
    private val _amountMaxFen = MutableStateFlow<Long?>(null)
    val amountMaxFen: StateFlow<Long?> = _amountMaxFen.asStateFlow()

    private val _categoryId = MutableStateFlow<String?>(null)
    val categoryId: StateFlow<String?> = _categoryId.asStateFlow()

    /** 日期起（yyyy-MM-dd）；null=未设置（不过滤）。 */
    private val _dateFrom = MutableStateFlow<String?>(null)
    val dateFrom: StateFlow<String?> = _dateFrom.asStateFlow()

    /** 日期止（yyyy-MM-dd）；null=未设置（不过滤）。 */
    private val _dateTo = MutableStateFlow<String?>(null)
    val dateTo: StateFlow<String?> = _dateTo.asStateFlow()

    /** 收支类型；null=全部。 */
    private val _type = MutableStateFlow<TransactionType?>(null)
    val type: StateFlow<TransactionType?> = _type.asStateFlow()

    // ==== 债务组条件 ====
    private val _partyName = MutableStateFlow("")
    val partyName: StateFlow<String> = _partyName.asStateFlow()

    /** 方向；null=全部（借出/借入，见 [DebtDirection]）。 */
    private val _direction = MutableStateFlow<DebtDirection?>(null)
    val direction: StateFlow<DebtDirection?> = _direction.asStateFlow()

    // ==== 条件组切换 ====
    private val _tab = MutableStateFlow(SearchTab.TRANSACTION)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    /** 手动刷新触发：递增使 `flatMapLatest` 重订阅观察流（冷流重连，供错误重试）。 */
    private val refreshTick = MutableStateFlow(0)

    /**
     * 候选分类流（当前账本、全类型）：供分类筛选 [com.minibook.ui.components.CategoryPickerSheet]
     * 按 `type` 过滤，以及结果行/明细联表解析分类名/图标/色。
     * 账本切换经 ObserveCategoriesUseCase 自动重订阅。
     */
    val categories: StateFlow<List<Category>> = observeCategories.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 金额区间是否无效（`min > max`）：true 时视为无效查询、不执行搜索，
     * UI 据此在金额两输入框下方展示错误文案（UI §8.1 Error 态）。
     */
    val amountRangeInvalid: StateFlow<Boolean> = combine(_amountMinFen, _amountMaxFen) { min, max ->
        min != null && max != null && min > max
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 页面四态：[SearchUiState.Loading] 流未首发射（骨架屏）；[SearchUiState.Empty] 无结果；
     * [SearchUiState.Error] 金额区间无效 / 账本未初始化 / 数据库异常；[SearchUiState.Success]
     * 非空交易+债务两组。
     *
     * 关键词（[remark]/[partyName]）经 `debounce(300ms)`，其余字段即时 → 组合为
     * [SearchConditions] 流 → `flatMapLatest` 依当前条件重订阅两个观察流。
     */
    val uiState: StateFlow<SearchUiState> =
        combine(
            combine(
                combine(
                    _remark.debounce(KEYWORD_DEBOUNCE_MS),
                    _amountMinFen,
                    _amountMaxFen,
                    _categoryId,
                ) { remark, min, max, cat ->
                    TxFieldA(remark, min, max, cat)
                },
                combine(
                    _dateFrom,
                    _dateTo,
                    _type,
                ) { df, dt, type ->
                    TxFieldB(df, dt, type)
                },
            ) { a, b ->
                TxConditions(
                    remark = a.remark,
                    amountMinFen = a.amountMinFen,
                    amountMaxFen = a.amountMaxFen,
                    categoryId = a.categoryId,
                    dateFrom = b.dateFrom,
                    dateTo = b.dateTo,
                    type = b.type,
                )
            },
            combine(
                _partyName.debounce(KEYWORD_DEBOUNCE_MS),
                _direction,
            ) { name, dir ->
                DebtConditions(name, dir)
            },
            refreshTick,
        ) { tx, debt, _ ->
            SearchConditions(tx, debt)
        }
            .flatMapLatest { conditions ->
                when {
                    // 金额区间 min>max：视为无效，不执行查询。
                    conditions.amountRangeInvalid() -> flowOf(SearchResultSource.InvalidConditions)
                    // 未输入任何条件：不执行查询，展示「开始搜索」引导（UI §8.1 Empty 态）。
                    !conditions.hasActiveFilter() -> flowOf(SearchResultSource.IdleConditions)
                    else -> combine(
                        searchTransactions(conditions.toTransactionFilter()),
                        searchDebts(conditions.toDebtFilter()),
                        categories,
                    ) { transactions, debts, cats ->
                        SearchResultSource.ConditionsResult(conditions, transactions, debts, cats)
                    }
                }
            }
            .map { result ->
                when (result) {
                    SearchResultSource.IdleConditions -> SearchUiState.Empty(hasActiveFilter = false)
                    SearchResultSource.InvalidConditions -> SearchUiState.Error(AMOUNT_RANGE_ERROR_MESSAGE)
                    is SearchResultSource.ConditionsResult -> {
                        if (result.transactions.isEmpty() && result.debts.isEmpty()) {
                            SearchUiState.Empty(hasActiveFilter = result.conditions.hasActiveFilter())
                        } else {
                            SearchUiState.Success(
                                transactions = result.transactions.sortedWith(TRANSACTION_ORDER),
                                debts = result.debts.sortedWith(DEBT_ORDER),
                                categoriesById = result.categories.associateBy { it.id },
                            )
                        }
                    }
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(
                    SearchUiState.Error(
                        if (e is LedgerNotFound) LEDGER_ERROR_MESSAGE else LOAD_ERROR_MESSAGE,
                    ),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

    // ==== 条件组 ====

    /** 切换条件组（交易 / 债务），两组各自保留已填值、不互相清空。 */
    fun setTab(tab: SearchTab) {
        _tab.value = tab
    }

    fun setRemark(value: String) {
        _remark.value = value
    }

    fun setAmountMinFen(fen: Long?) {
        _amountMinFen.value = fen
    }

    fun setAmountMaxFen(fen: Long?) {
        _amountMaxFen.value = fen
    }

    fun setCategoryId(id: String?) {
        _categoryId.value = id
    }

    fun setDateFrom(date: String?) {
        _dateFrom.value = date
    }

    fun setDateTo(date: String?) {
        _dateTo.value = date
    }

    fun setType(type: TransactionType?) {
        _type.value = type
    }

    fun setPartyName(value: String) {
        _partyName.value = value
    }

    fun setDirection(direction: DebtDirection?) {
        _direction.value = direction
    }

    /** 「重置」：一键清空**当前组**全部条件（UI §8.1），结果随条件置空恢复。 */
    fun resetCurrentTab() {
        when (_tab.value) {
            SearchTab.TRANSACTION -> {
                _remark.value = ""
                _amountMinFen.value = null
                _amountMaxFen.value = null
                _categoryId.value = null
                _dateFrom.value = null
                _dateTo.value = null
                _type.value = null
            }
            SearchTab.DEBT -> {
                _partyName.value = ""
                _direction.value = null
            }
        }
    }

    /** 「清除条件」：清空**全部**条件（空态「清除条件」动作，保证结果恢复）。 */
    fun clearAll() {
        _remark.value = ""
        _amountMinFen.value = null
        _amountMaxFen.value = null
        _categoryId.value = null
        _dateFrom.value = null
        _dateTo.value = null
        _type.value = null
        _partyName.value = ""
        _direction.value = null
    }

    /** 手动刷新：重订阅观察流（`WhileSubscribed` 已保证回页自动刷新，本方法供错误重试）。 */
    fun refresh() {
        refreshTick.value += 1
    }

    companion object {
        /** 关键词防抖间隔（UI §8.1，关键词类字段）。 */
        const val KEYWORD_DEBOUNCE_MS = 300L

        /** 金额区间 `min > max` 无效提示（UI §8.1 Error 态，视为无效不执行）。 */
        const val AMOUNT_RANGE_ERROR_MESSAGE = "最小金额不能大于最大金额"

        /** 当前账本未初始化文案（对齐 TransactionsViewModel §8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 未知异常兜底文案。 */
        const val LOAD_ERROR_MESSAGE = "加载失败，请稍后重试"
    }
}