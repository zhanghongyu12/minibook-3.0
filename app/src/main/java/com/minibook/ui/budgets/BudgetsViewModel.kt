package com.minibook.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.BudgetProgress
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.usecase.budget.ClearCategoryBudgetUseCase
import com.minibook.domain.usecase.budget.ObserveBudgetProgressUseCase
import com.minibook.domain.usecase.budget.SetCategoryBudgetUseCase
import com.minibook.domain.usecase.budget.SetTotalBudgetUseCase
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 预算管理页 ViewModel（TASK-038，UI §8.3 P-14 / DEC-008 / DEC-012）。
 *
 * 职责：
 * - 持有月份态 [month]（`java.time.YearMonth`，初始当前月），[setMonth] 切换后由
 *   `flatMapLatest` 驱动 [ObserveBudgetProgressUseCase.observe] 重订阅（月份变化 → 重订阅，
 *   账本切换由 UseCase 内置重订阅，UI 层不感知账本）。
 * - 折叠为四态 [BudgetsUiState]（Loading / Empty / Error / Success），供 [BudgetsScreen] 直接渲染：
 *   未设任何预算（总额=0 且无分类预算）→ [BudgetsUiState.Empty]；否则 → Success。
 * - **编辑动作**（金额一律 `Long` 分，校验走 [SetTotalBudgetUseCase] 等 UseCase 内置
 *   `Validators.validateAmount`）：
 *   - [setTotalBudget]：`SetTotalBudgetUseCase`（总额「先删后插」在 Repository 事务内闭合，
 *     DEC-008），成功 emit [saved]（UI 关弹层）+ snackbar；
 *   - [setCategoryBudget]：`SetCategoryBudgetUseCase`（分类预算唯一由 DB
 *     `UNIQUE(ledgerId, month, categoryId)` 保证，DEC-008），upsert；
 *   - [clearCategoryBudget]：`ClearCategoryBudgetUseCase` 物理删除。
 * - 编辑错误经 [editorError]（弹层内联展示），保存/删除结果经 [snackbar]（页面轻提示）。
 * - **分类选择数据**：[expenseCategories]（type=expense，账本内置重订阅）供分类预算编辑弹层的
 *   分类选择器（C-02 限定支出）；分类元信息已由 `CategoryBudgetProgress.category` 携带，
 *   页面无需再经分类流做 id→元信息映射。
 *
 * 冷流语义：`observe` 为冷流（仅订阅时执行），故以 `WhileSubscribed(5_000)` 保活——UI 离屏
 * 5 秒后自动停止订阅，回到页面重连即刷新。[refresh] 通过递增 [refreshTick] 使 `flatMapLatest`
 * 重订阅冷流，供外部显式刷新（如错误重试）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val observeBudgetProgress: ObserveBudgetProgressUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val setTotalBudgetUseCase: SetTotalBudgetUseCase,
    private val setCategoryBudgetUseCase: SetCategoryBudgetUseCase,
    private val clearCategoryBudgetUseCase: ClearCategoryBudgetUseCase,
) : ViewModel() {

    /** 当前查看月份（初始当前月，`java.time.YearMonth`）。 */
    private val _month = MutableStateFlow(YearMonth.now())

    /** 对外只读的当前月份。 */
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** 显式刷新触发：递增使 `flatMapLatest` 重订阅观察流（冷流重连，供错误重试）。 */
    private val refreshTick = MutableStateFlow(0)

    /**
     * 页面四态：[Loading] 流未首发射（骨架屏）；[Empty] 未设任何预算（引导设置总预算）；
     * [Error] 观察流异常（账本未初始化 / 数据库异常，供 UI 展示重试）；[Success] 预算进度。
     */
    val uiState: StateFlow<BudgetsUiState> = combine(_month, refreshTick) { m, _ -> m }
        .flatMapLatest { month -> observeBudgetProgress.observe(month) }
        .map { progress ->
            // 未设任何预算（总额=0 且无分类预算）→ 空态引导；有任一预算 → Success。
            if (progress.totalBudgetFen <= 0L && progress.categoryProgress.isEmpty()) {
                BudgetsUiState.Empty
            } else {
                BudgetsUiState.Success(progress)
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(
                BudgetsUiState.Error(
                    if (e is LedgerNotFound) LEDGER_ERROR_MESSAGE else LOAD_ERROR_MESSAGE,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState.Loading)

    /** 支出分类流（供分类预算编辑弹层的分类选择器；账本切换自动重订阅）。 */
    val expenseCategories: StateFlow<List<Category>> = observeCategories
        .observe(CategoryType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 页面轻提示文案（保存/删除结果）。 */
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /** 编辑弹层内联错误流（保存失败内联展示，成功后自动清除）。 */
    private val _editorError = MutableStateFlow<String?>(null)
    val editorError: StateFlow<String?> = _editorError.asStateFlow()

    /** 编辑保存/删除进行中：弹层确认按钮置灰，防重复提交。 */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** 编辑保存/删除成功事件：UI 据此关闭编辑弹层。 */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    /**
     * 切换查看月份；月份变化 → `flatMapLatest` 重订阅 [ObserveBudgetProgressUseCase.observe]
     * （重算当月总额/分类预算与已用，DATABASE §10.4）。
     *
     * @param month 目标月份。
     */
    fun setMonth(month: YearMonth) {
        _month.value = month
    }

    /**
     * 显式刷新：重订阅预算进度观察流（`WhileSubscribed` 已保证回页自动刷新，
     * 本方法供 UI 事件驱动的刷新 / 错误重试使用）。
     */
    fun refresh() {
        refreshTick.value += 1
    }

    /**
     * 设置当前月份的月度总预算（总额「先删后插」唯一，DEC-008）。
     *
     * 成功 → snackbar + emit [saved]（UI 关弹层）；失败 → [editorError]（弹层内联展示）。
     *
     * @param amount 金额（**分**，>0 且 ≤ 上限；UI 元→分换算经 MoneyFormatter）。
     */
    fun setTotalBudget(amount: Long) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                setTotalBudgetUseCase(_month.value, amount)
                _snackbar.value = "已保存月度总预算"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _editorError.value = e.message?.ifBlank { null } ?: "保存失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * 设置当前月份某分类的分类预算（upsert，唯一由 DB `UNIQUE(ledgerId, month, categoryId)`
     * 约束保证，DEC-008）。
     *
     * 成功 → snackbar + emit [saved]；失败 → [editorError]。
     *
     * @param categoryId 分类 id（必填）。
     * @param amount 金额（**分**，>0 且 ≤ 上限）。
     */
    fun setCategoryBudget(categoryId: String, amount: Long) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                setCategoryBudgetUseCase(_month.value, categoryId, amount)
                _snackbar.value = "已保存分类预算"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _editorError.value = e.message?.ifBlank { null } ?: "保存失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * 删除当前月份某分类的分类预算（物理删除，幂等）。
     *
     * 成功 → snackbar + emit [saved]；失败 → [editorError]。
     *
     * @param categoryId 待删除分类预算所属分类 id。
     */
    fun clearCategoryBudget(categoryId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                clearCategoryBudgetUseCase(_month.value, categoryId)
                _snackbar.value = "已删除分类预算"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _editorError.value = e.message?.ifBlank { null } ?: "删除失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** 清除页面轻提示（UI 展示后调用）。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }

    /** 清除编辑弹层内联错误（弹层关闭/成功后调用）。 */
    fun clearEditorError() {
        _editorError.value = null
    }

    companion object {
        /** 当前账本未初始化文案（对齐 HomeViewModel §8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 未知异常兜底文案。 */
        const val LOAD_ERROR_MESSAGE = "加载失败，请稍后重试"
    }
}

/**
 * 预算管理页 UI 四态（TASK-038，UI §8.3 P-14）。
 */
sealed interface BudgetsUiState {

    /** 流未首发射 / 首启加载中：展示 [com.minibook.ui.components.LoadingState]。 */
    data object Loading : BudgetsUiState

    /** 未设任何预算（总额=0 且无分类预算）：展示「设置月度总预算」引导。 */
    data object Empty : BudgetsUiState

    /** 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 重试。 */
    data class Error(val message: String) : BudgetsUiState

    /** 有任一预算：总额卡 + 分类预算区，展示已用/剩余/进度与超支标红。 */
    data class Success(val progress: BudgetProgress) : BudgetsUiState
}
