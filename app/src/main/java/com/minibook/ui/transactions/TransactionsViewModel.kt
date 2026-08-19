package com.minibook.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.transaction.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 收支记录页 ViewModel（TASK-024，UI §2 P-03）。
 *
 * 职责：
 * - 持有类型筛选态 [filterType]（null=全部 / EXPENSE / INCOME），[setFilter] 更新后由
 *   `flatMapLatest` 触发 [ObserveTransactionsUseCase.observe] 重订阅（filter 变化 → 重新
 *   collect，UseCase 内置账本切换重订阅，UI 层不感知账本）。
 * - 将交易流折叠为三态 [TransactionsUiState]（Loading / Empty / Success），供
 *   [TransactionsScreen] 直接渲染。VM 只提供扁平列表，**按日期分组在 UI 层完成**。
 *
 * 冷流语义：`observe` 为冷流（仅订阅时执行），故以 `WhileSubscribed(5_000)` 保活——UI 离屏
 * 5 秒后自动停止订阅，回到页面重连即刷新（避免后台常驻查询）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    observeTransactions: ObserveTransactionsUseCase,
) : ViewModel() {

    /** 当前类型筛选：null=全部；EXPENSE=支出；INCOME=收入（初值全部）。 */
    private val _filterType = MutableStateFlow<TransactionType?>(null)

    /** 对外只读的类型筛选态。 */
    val filterType: StateFlow<TransactionType?> = _filterType.asStateFlow()

    /** 页面三态：[Loading] 流未首发射（骨架屏）；[Empty] 空列表；[Success] 非空扁平列表。 */
    val uiState: StateFlow<TransactionsUiState> = _filterType
        .flatMapLatest { type ->
            observeTransactions.observe(TransactionFilter(type = type))
        }
        .map { list ->
            if (list.isEmpty()) {
                TransactionsUiState.Empty(hasActiveFilter = _filterType.value != null)
            } else {
                TransactionsUiState.Success(list)
            }
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

    /** 非空扁平交易列表（组内 createdAt DESC 由 DAO 保证，分组在 UI 层完成）。 */
    data class Success(val transactions: List<Transaction>) : TransactionsUiState
}
