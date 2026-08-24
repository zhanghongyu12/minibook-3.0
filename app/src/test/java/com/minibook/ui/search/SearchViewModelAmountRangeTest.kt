package com.minibook.ui.search

import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.search.SearchDebtsUseCase
import com.minibook.domain.usecase.search.SearchTransactionsUseCase
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeDebtRepository
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.fakeLedgerDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SearchViewModel 金额区间 min>max 校验逻辑单测（TASK-033，PRD F-06 / DEC-013）。
 *
 * 依赖较重的 UI 层仅在「校验判定」（JVM 可见的纯逻辑）层面测：
 * - [SearchViewModel.amountRangeInvalid]：`min != null && max != null && min > max`；
 * - [SearchUiState.Error]（AMOUNT_RANGE_ERROR_MESSAGE）：min>max 视为无效查询、不执行搜索。
 *
 * 说明：ViewModel 依赖 [androidx.lifecycle.ViewModel] 与 `viewModelScope`（Main 调度器），
 * 故用 `kotlinx-coroutines-test` 的 [StandardTestDispatcher] + [Dispatchers.setMain]
 * 提供一个可控 Main 调度器；其余三分支（Empty/Success 及两组结果合并）属 UI 交互流，
 * 不在本阶段单测范围（见 09_test_report.md「已知遗留」）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelAmountRangeTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- amountRangeInvalid：min>max 判定 ----

    @Test
    fun `min 大于 max - amountRangeInvalid 为 true`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.activate(vm)
        vm.setAmountMinFen(100L)
        vm.setAmountMaxFen(50L)
        advanceTimeBy(SearchViewModel.KEYWORD_DEBOUNCE_MS + 1)
        assertTrue("min(100) > max(50) 应视为无效", vm.amountRangeInvalid.value)
    }

    @Test
    fun `min 等于 max - amountRangeInvalid 为 false（合法）`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.activate(vm)
        vm.setAmountMinFen(100L)
        vm.setAmountMaxFen(100L)
        advanceTimeBy(SearchViewModel.KEYWORD_DEBOUNCE_MS + 1)
        assertFalse("min==max 合法，不应判为无效", vm.amountRangeInvalid.value)
    }

    @Test
    fun `只设 min 或 max - amountRangeInvalid 为 false（不过滤单边）`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.activate(vm)
        // 只设下限
        vm.setAmountMinFen(100L)
        advanceTimeBy(SearchViewModel.KEYWORD_DEBOUNCE_MS + 1)
        assertFalse(vm.amountRangeInvalid.value)

        // 只设上限
        vm.setAmountMaxFen(50L)
        vm.setAmountMinFen(null)
        advanceTimeBy(SearchViewModel.KEYWORD_DEBOUNCE_MS + 1)
        assertFalse(vm.amountRangeInvalid.value)
    }

    // ---- uiState：min>max → Error（AMOUNT_RANGE_ERROR_MESSAGE） ----

    @Test
    fun `min 大于 max - uiState 折为 Error 且不执行搜索`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.activate(vm)
        vm.setAmountMinFen(200L)
        vm.setAmountMaxFen(100L)
        advanceTimeBy(SearchViewModel.KEYWORD_DEBOUNCE_MS + 1)

        val state = vm.uiState.value
        assertTrue("min>max 应折为 Error 态", state is SearchUiState.Error)
        assertEquals(SearchViewModel.AMOUNT_RANGE_ERROR_MESSAGE, (state as SearchUiState.Error).message)
    }

    // ---- 构造 ----

    private fun newViewModel(): SearchViewModel = SearchViewModel(
        searchTransactions = SearchTransactionsUseCase(
            transactionRepository = FakeTransactionRepository(),
            ledgerDataStore = fakeLedgerDataStore(), // 空串，不触达仓库
        ),
        searchDebts = SearchDebtsUseCase(
            debtRepository = FakeDebtRepository(),
            ledgerDataStore = fakeLedgerDataStore(),
        ),
        observeCategories = ObserveCategoriesUseCase(
            ledgerDataStore = fakeLedgerDataStore(),
            categoryRepository = FakeCategoryRepository(),
        ),
    )

    /** 订阅 [SearchViewModel.amountRangeInvalid] / [SearchViewModel.uiState]，驱动 WhileSubscribed 启动。 */
    private fun kotlinx.coroutines.CoroutineScope.activate(vm: SearchViewModel) {
        launch { vm.amountRangeInvalid.collect { } }
        launch { vm.uiState.collect { } }
    }
}
