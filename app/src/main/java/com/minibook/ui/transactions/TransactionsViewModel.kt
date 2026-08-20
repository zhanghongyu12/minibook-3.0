package com.minibook.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
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

    /** 手动刷新触发：递增使 `flatMapLatest` 重订阅观察流（冷流重连，供错误重试）。 */
    private val refreshTick = MutableStateFlow(0)

    /**
     * 页面四态：[Loading] 流未首发射（骨架屏）；[Empty] 空列表；[Error] 观察流异常
     * （账本未初始化 / 数据库异常，供 UI 展示重试）；[Success] 非空扁平列表。
     */
    val uiState: StateFlow<TransactionsUiState> = combine(_filterType, refreshTick) { type, _ -> type }
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

    /** 非空扁平交易列表（组内 createdAt DESC 由 DAO 保证，分组在 UI 层完成）。 */
    data class Success(val transactions: List<Transaction>) : TransactionsUiState
}
