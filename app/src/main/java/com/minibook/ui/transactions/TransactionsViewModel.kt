package com.minibook.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Category
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.transaction.BatchUpdateTransactionsUseCase
import com.minibook.domain.usecase.transaction.DeleteTransactionsUseCase
import com.minibook.domain.usecase.transaction.ObserveTransactionsUseCase
import com.minibook.ui.navigation.BillsFilterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 收支记录页 ViewModel（TASK-024，UI §2 P-03）。
 *
 * 职责：
 * - 持有类型筛选态 [filterType]（null=全部 / EXPENSE / INCOME）、分类筛选态
 *   [filterCategoryId]（null=全部分类）、时间段 [dateFrom]/[dateTo]（null=该端不限）与
 *   备注模糊关键词 [remark]（null=不按备注过滤）；任一变化经 `combine` + `flatMapLatest`
 *   触发 [ObserveTransactionsUseCase.observe] 重订阅（filter 变化 → 重新 collect，UseCase
 *   内置账本切换重订阅，UI 层不感知账本）。
 * - 将交易流折叠为三态 [TransactionsUiState]（Loading / Empty / Success），供
 *   [TransactionsScreen] 直接渲染。VM 只提供扁平列表，**按日期分组在 UI 层完成**。
 * - **分类联表**：分类流经 [ObserveCategoriesUseCase.observe] 与交易流同账本同源（同 TASK-015，
 *   内置账本切换重订阅），在 [TransactionsUiState.Success] 侧折叠为
 *   `categoriesById`（id→[Category]）供 UI 联表展示（对齐首页 0c041a3）。
 * - **查询区**：对外暴露 [categories]（当前账本全类型分类流，常驻显示于列表上方，不随空态
 *   消失），供 UI 渲染「搜索框 + 时间段 + 收缩式分类入口」；报表等跳转落点即本查询框
 *   （见 [applyBillsFilter]）。
 *
 * 冷流语义：`observe` 为冷流（仅订阅时执行），故以 `WhileSubscribed(5_000)` 保活——UI 离屏
 * 5 秒后自动停止订阅，回到页面重连即刷新（避免后台常驻查询）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    observeTransactions: ObserveTransactionsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val deleteTransactionsUseCase: DeleteTransactionsUseCase,
    private val batchUpdateTransactions: BatchUpdateTransactionsUseCase,
    private val ledgerProvider: LedgerProvider,
) : ViewModel() {

    /** 当前类型筛选：null=全部；EXPENSE=支出；INCOME=收入（初值全部）。 */
    private val _filterType = MutableStateFlow<TransactionType?>(null)

    /** 对外只读的类型筛选态。 */
    val filterType: StateFlow<TransactionType?> = _filterType.asStateFlow()

    /** 当前分类筛选 id：null=全部分类（初值全部）。 */
    private val _filterCategoryId = MutableStateFlow<String?>(null)

    /** 对外只读的分类筛选态。 */
    val filterCategoryId: StateFlow<String?> = _filterCategoryId.asStateFlow()

    /** 查询框时间段起点 `yyyy-MM-dd`；null=未设起点（不限起始）。 */
    private val _dateFrom = MutableStateFlow<String?>(null)

    /** 对外只读的查询框时间段起点。 */
    val dateFrom: StateFlow<String?> = _dateFrom.asStateFlow()

    /** 查询框时间段终点 `yyyy-MM-dd`；null=未设终点（不限结束）。 */
    private val _dateTo = MutableStateFlow<String?>(null)

    /** 对外只读的查询框时间段终点。 */
    val dateTo: StateFlow<String?> = _dateTo.asStateFlow()

    /** 备注/标题模糊查询关键词；null=不按备注过滤（空串/纯空白归一为 null）。 */
    private val _remark = MutableStateFlow<String?>(null)

    /** 对外只读的备注模糊关键词。 */
    val remark: StateFlow<String?> = _remark.asStateFlow()

    /** 手动刷新触发：递增使 `flatMapLatest` 重订阅观察流（冷流重连，供错误重试）。 */
    private val refreshTick = MutableStateFlow(0)

    /**
     * 候选分类流（当前账本、全类型）：供分类筛选栏（横向 chip + 下拉）常驻显示，不随
     * 列表空态消失。账本切换经 [ObserveCategoriesUseCase.observe] 自动重订阅
     * （对齐 SearchViewModel 的 [com.minibook.ui.search.SearchViewModel.categories] 范式）。
     */
    val categories: StateFlow<List<Category>> = observeCategories.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 页面四态：[Loading] 流未首发射（骨架屏）；[Empty] 空列表；[Error] 观察流异常
     * （账本未初始化 / 数据库异常，供 UI 展示重试）；[Success] 非空扁平列表。
     *
     * 组合交易流 + 分类流：交易流经 [filterType]+[filterCategoryId] combine 后 `flatMapLatest`
     * 驱动（任一 filter 变化即重订阅），分类流（[ObserveCategoriesUseCase.observe]，
     * filter=null 全部）与交易流同账本同源——在 [TransactionsUiState.Success] 侧折叠为
     * `categoriesById`（id→[Category]），分类改名/换色时只重发分类流即实时联动
     * （协调日志 2026-08-21 commit 0c041a3 同款模式）。
     */
    val uiState: StateFlow<TransactionsUiState> = combine(
        combine(
            combine(_filterType, _filterCategoryId, _dateFrom, _dateTo, _remark) { type, categoryId, from, to, remark ->
                TransactionFilter(type = type, categoryId = categoryId, dateFrom = from, dateTo = to, remark = remark)
            },
            refreshTick,
        ) { filter, _ -> filter }
            .flatMapLatest { filter ->
                observeTransactions.observe(filter)
            },
        observeCategories.observe(),
    ) { transactions, categories ->
        TransactionSource(transactions, categories)
    }
        .map { source ->
            if (source.transactions.isEmpty()) {
                TransactionsUiState.Empty(
                    hasActiveFilter = _filterType.value != null ||
                        _filterCategoryId.value != null ||
                        _dateFrom.value != null ||
                        _dateTo.value != null ||
                        !_remark.value.isNullOrBlank(),
                )
            } else {
                TransactionsUiState.Success(
                    transactions = source.transactions,
                    categoriesById = source.categories.associateBy { it.id },
                )
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(
                TransactionsUiState.Error(
                    if (e is LedgerNotFound) LEDGER_ERROR_MESSAGE else LOAD_ERROR_MESSAGE,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState.Loading)

    /**
     * 应用报表分类占比点击产生的账单筛选请求：把 type/category/dateRange 直接填入查询框
     * （跳转落点 = 查询框，不再有独立提示条），备注清空。不在此 clear
     * [com.minibook.ui.navigation.BillsFilterBus.request]——由调用方（[TransactionsScreen]
     * 组合作用域的 LaunchedEffect 消费后）负责 clear，确保「切 Tab」先于「消费」。
     */
    fun applyBillsFilter(req: BillsFilterRequest) {
        setFilter(req.type)
        setCategoryFilter(req.categoryId)
        setDateRange(req.dateFrom, req.dateTo)
        setRemark(null)
    }

    /**
     * 设置类型筛选；null=全部（含空态「清除筛选」回调）。
     *
     * @param type 目标筛选类型；null 表示「全部」。
     */
    fun setFilter(type: TransactionType?) {
        _filterType.value = type
    }

    /**
     * 设置分类筛选；null=全部分类（分类下拉「全部分类」/ 空态「清除筛选」均走此口径）。
     *
     * @param categoryId 目标分类 id；null 表示「全部分类」。
     */
    fun setCategoryFilter(categoryId: String?) {
        _filterCategoryId.value = categoryId
    }

    /**
     * 设置备注/标题模糊查询关键词；null 或纯空白 = 不按备注过滤（归一为 null）。
     */
    fun setRemark(remark: String?) {
        _remark.value = remark?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 设置查询框时间段（起止 `yyyy-MM-dd`）；任一传 null = 该端不限（清空）。
     */
    fun setDateRange(from: String?, to: String?) {
        _dateFrom.value = from
        _dateTo.value = to
    }

    /**
     * 清除全部查询条件（类型 + 分类 + 时间段 + 备注）：供空态「清除筛选」与查询区「重置」
     * 使用。[hasActiveFilter] 现涵盖 type/categoryId/dateFrom/dateTo/remark 任一非空，
     * 故须一并复位全部维度。
     */
    fun clearFilter() {
        _filterType.value = null
        _filterCategoryId.value = null
        _dateFrom.value = null
        _dateTo.value = null
        _remark.value = null
    }

    /**
     * 手动刷新：重订阅交易观察流（`WhileSubscribed` 已保证回页自动刷新，
     * 本方法供 UI 事件驱动的刷新 / 错误重试使用）。
     */
    fun refresh() {
        refreshTick.value += 1
    }

    /**
     * 批量删除交易（记账页批量管理模式）：委托 [DeleteTransactionsUseCase]。
     *
     * 说明：批量管理的选中集合 / 批量模式开关为页内 `remember` 自持（[TransactionsScreen]），
     * VM 只承载「删除」这一动作（空列表由 UseCase 短路返回 0）。删除后交易观察流（Room
     * Flow）自动重发 → [uiState] 自动刷新，无需手动 refresh。
     *
     * @param ids 待删除交易 id 集合。
     * @return 实际删除行数。
     */
    suspend fun deleteTransactions(ids: List<String>): Int = deleteTransactionsUseCase(ids)

    /**
     * 批量改分类：委托 [BatchUpdateTransactionsUseCase.updateCategory]（含自学习副作用）。
     * 当前账本经 [LedgerProvider] 取（对齐 ImportViewModel 口径）。
     */
    suspend fun batchUpdateCategory(ids: List<String>, categoryId: String) {
        batchUpdateTransactions.updateCategory(ledgerProvider.currentLedgerId(), ids, categoryId)
    }

    /**
     * 批量改类型：委托 [BatchUpdateTransactionsUseCase.updateType]（分类归位「其他」，不触发自学习）。
     */
    suspend fun batchUpdateType(ids: List<String>, type: TransactionType) {
        batchUpdateTransactions.updateType(ledgerProvider.currentLedgerId(), ids, type)
    }

    companion object {
        /** 当前账本未初始化文案（对齐 HomeViewModel §8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 未知异常兜底文案。 */
        const val LOAD_ERROR_MESSAGE = "加载失败，请稍后重试"
    }
}

/**
 * 收支记录页 UI 三态（TASK-024，UI §2 P-03）。
 *
 * @property hasActiveFilter 空态下是否处于筛选状态（true 时展示「清除筛选」动作）。
 */
sealed interface TransactionsUiState {

    /** 流未首发射 / 首启加载中：展示 [com.minibook.ui.components.LoadingState]。 */
    data object Loading : TransactionsUiState

    /** 空列表：[hasActiveFilter]=true 提示清除筛选；false 提示暂无记录。 */
    data class Empty(val hasActiveFilter: Boolean) : TransactionsUiState

    /** 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 重试。 */
    data class Error(val message: String) : TransactionsUiState

    /** 非空扁平交易列表（组内 createdAt DESC 由 DAO 保证，分组在 UI 层完成）+ 分类 id→[Category] 映射（联表展示用）。 */
    data class Success(
        val transactions: List<Transaction>,
        val categoriesById: Map<String, Category>,
    ) : TransactionsUiState
}

/** [TransactionsViewModel] 组合源数据：交易列表 + 分类列表（id→[Category] 联表用）。 */
private data class TransactionSource(
    val transactions: List<Transaction>,
    val categories: List<Category>,
)
