package com.minibook.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.usecase.debt.NetByPartyUseCase
import com.minibook.domain.usecase.debt.ObserveDebtsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 债务管理页 ViewModel（TASK-025，UI §2 P-04）。
 *
 * 职责：
 * - 持有页签态 [tabIndex]（0=明细 / 1=按对象），[setTab] 更新，初值明细。
 * - 组合数据流：订阅 [ObserveDebtsUseCase.observe] 明细流（内置账本切换重订阅），每次债务
 *   变化经 [NetByPartyUseCase] **重算**按对象净额（`details to byParty`）。净额口径
 *   （PRD F-02：lend×owe_me=+ / lend×i_owe=− / repay×owe_me=− / repay×i_owe=+）由
 *   DAO / 组件内置，VM 不重复计算，只做 `byParty.values.sum()` 的**净额合计**。
 * - 首启 bootstrap 前当前账本未初始化，[NetByPartyUseCase] 会抛 [LedgerNotFound]——
 *   [safeNetByParty] 容错降级为空映射（明细流此时亦为空，页面呈空态，属正常过渡态）。
 * - 折叠为三态 [DebtsUiState]（Loading / Empty / Success），供 [DebtsScreen] 直接渲染。
 *
 * 冷流语义：`observe` 为冷流（仅订阅时执行），故以 `WhileSubscribed(5_000)` 保活——UI 离屏
 * 5 秒后自动停止订阅，回到页面重连即刷新。[refresh] 通过递增 [refreshTick] 使 `flatMapLatest`
 * 重订阅冷流，供外部显式刷新（如错误重试 / 下拉刷新）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val observeDebts: ObserveDebtsUseCase,
    private val netByParty: NetByPartyUseCase,
) : ViewModel() {

    /** 当前页签：0=明细，1=按对象（初值明细）。 */
    private val _tabIndex = MutableStateFlow(0)

    /** 对外只读页签态。 */
    val tabIndex: StateFlow<Int> = _tabIndex.asStateFlow()

    /** 显式刷新触发：递增使 `flatMapLatest` 重订阅观察流（冷流重连）。 */
    private val refreshTick = MutableStateFlow(0)

    /**
     * 页面四态：[Loading] 流未首发射（骨架屏）；[Empty] 无任何债务；[Error] 观察流异常
     * （账本未初始化 / 数据库异常，供 UI 展示重试）；[Success] 明细+按对象净额+净额合计。
     */
    val uiState: StateFlow<DebtsUiState> = refreshTick
        .flatMapLatest {
            observeDebts.observe(DebtFilter())
                .map { details -> details to safeNetByParty() }
        }
        .map { (details, byParty) ->
            val totalNet = byParty.values.sum()
            if (details.isEmpty() && byParty.isEmpty()) {
                DebtsUiState.Empty
            } else {
                DebtsUiState.Success(details, byParty, totalNet)
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(
                DebtsUiState.Error(
                    if (e is LedgerNotFound) LEDGER_ERROR_MESSAGE else LOAD_ERROR_MESSAGE,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState.Loading)

    /**
     * 切换页签。
     *
     * @param index 目标下标（0=明细，1=按对象）。
     */
    fun setTab(index: Int) {
        _tabIndex.value = index
    }

    /**
     * 显式刷新：重订阅明细观察流并重算净额（`WhileSubscribed` 已保证回页自动刷新，
     * 本方法供 UI 事件驱动的刷新场景使用）。
     */
    fun refresh() {
        refreshTick.value++
    }

    /**
     * 按对象净额聚合，容错降级。
     *
     * 首启 bootstrap 前当前账本未初始化（[LedgerProvider.currentLedgerId] 抛
     * [LedgerNotFound]，ARCHITECTURE §8.5）时返回空映射——明细流同刻为空列表，页面呈
     * 空态即可，不让一次未初始化的净额查询中断整条数据流。
     */
    private suspend fun safeNetByParty(): Map<String, Long> =
        try {
            netByParty()
        } catch (e: LedgerNotFound) {
            emptyMap()
        }

    companion object {
        /** 当前账本未初始化文案（对齐 HomeViewModel §8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 未知异常兜底文案。 */
        const val LOAD_ERROR_MESSAGE = "加载失败，请稍后重试"
    }
}

/**
 * 债务管理页 UI 三态（TASK-025，UI §2 P-04）。
 *
 * @property details 明细扁平列表（账本内按时间倒序，按日期分组在 UI 层完成）。
 * @property byParty 按对方姓名净额映射（正值=对方欠我，负值=我欠对方，0=两清）。
 * @property totalNet 全部对象净额合计（`byParty.values.sum()`，VM 计算）。
 */
sealed interface DebtsUiState {

    /** 流未首发射 / 首启加载中：展示 [com.minibook.ui.components.LoadingState]。 */
    data object Loading : DebtsUiState

    /** 无任何债务记录（明细与按对象均为空）：展示 [com.minibook.ui.components.EmptyState]。 */
    data object Empty : DebtsUiState

    /** 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 重试。 */
    data class Error(val message: String) : DebtsUiState

    /** 非空债务数据：明细 + 按对象净额 + 净额合计。 */
    data class Success(
        val details: List<Debt>,
        val byParty: Map<String, Long>,
        val totalNet: Long,
    ) : DebtsUiState
}
