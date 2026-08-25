package com.minibook.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Category
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.transaction.ObserveTransactionsUseCase
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
 * - 持有类型筛选态 [filterType]（null=全部 / EXPENSE / INCOME）与分类筛选态
 *   [filterCategoryId]（null=全部分类）；任一变化经 `combine` + `flatMapLatest` 触发
 *   [ObserveTransactionsUseCase.observe] 重订阅（filter 变化 → 重新 collect，UseCase 内置
 *   账本切换重订阅，UI 层不感知账本）。
 * - 将交易流折叠为三态 [TransactionsUiState]（Loading / Empty / Success），供
 *   [TransactionsScreen] 直接渲染。VM 只提供扁平列表，**按日期分组在 UI 层完成**。
 * - **分类联表**：分类流经 [ObserveCategoriesUseCase.observe] 与交易流同账本同源（同 TASK-015，
 *   内置账本切换重订阅），在 [TransactionsUiState.Success] 侧折叠为
 *   `categoriesById`（id→[Category]）供 UI 联表展示（对齐首页 0c041a3）。
 * - **分类筛选栏**：对外暴露 [categories]（当前账本全类型分类流，常驻显示于列表上方，
 *   不随空态消失），供 UI 渲染横向 chip + 下拉选择器（分类 icon 为 emoji 文本）。
 *
 * 冷流语义：`observe` 为冷流（仅订阅时执行），故以 `WhileSubscribed(5_000)` 保活——UI 离屏
 * 5 秒后自动停止订阅，回到页面重连即刷新（避免后台常驻查询）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    observeTransactions: ObserveTransactionsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
) : ViewModel() {

    /** 当前类型筛选：null=全部；EXPENSE=支出；INCOME=收入（初值全部）。 */
    private val _filterType = MutableStateFlow<TransactionType?>(null)

    /** 对外只读的类型筛选态。 */
    val filterType: StateFlow<TransactionType?> = _filterType.asStateFlow()

    /** 当前分类筛选 id：null=全部分类（初值全部）。 */
    private val _filterCategoryId = MutableStateFlow<String?>(null)

    /** 对外只读的分类筛选态。 */
    val filterCategoryId: StateFlow<String?> = _filterCategoryId.asStateFlow()

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
        combine(_filterType, _filterCategoryId, refreshTick) { type, categoryId, _ -> type to categoryId }
            .flatMapLatest { (type, categoryId) ->
                observeTransactions.observe(TransactionFilter(type = type, categoryId = categoryId))
            },
        observeCategories.observe(),
    ) { transactions, categories ->
        TransactionSource(transactions, categories)
    }
        .map { source ->
            if (source.transactions.isEmpty()) {
                TransactionsUiState.Empty(
                    hasActiveFilter = _filterType.value != null || _filterCategoryId.value != null,
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
     * 设置类型筛选；null=全部（含空态「清除筛选」回调）。
     *
     * @param type 目标筛选类型；null 表示「全部」。
     */
    fun setFilter(type: TransactionType?) {
        _filterType.value = type
    }

    /**
     * 设置分类筛选；null=全部分类（「全部」chip / 空态「清除筛选」均走此口径）。
     *
     * @param categoryId 目标分类 id；null 表示「全部分类」。
     */
    fun setCategoryFilter(categoryId: String?) {
        _filterCategoryId.value = categoryId
    }

    /**
     * 清除全部筛选（类型 + 分类）：供空态「清除筛选」动作使用。空态 [hasActiveFilter]
     * 现为「type 或 categoryId 任一非空」，故须一并复位，避免只清类型残留分类。
     */
    fun clearFilter() {
        _filterType.value = null
        _filterCategoryId.value = null
    }

    /**
     * 手动刷新：重订阅交易观察流（`WhileSubscribed` 已保证回页自动刷新，
     * 本方法供 UI 事件驱动的刷新 / 错误重试使用）。
     */
    fun refresh() {
        refreshTick.value += 1
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
