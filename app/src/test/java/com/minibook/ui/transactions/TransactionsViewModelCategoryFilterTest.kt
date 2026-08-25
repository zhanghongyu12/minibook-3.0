package com.minibook.ui.transactions

import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.transaction.DeleteTransactionsUseCase
import com.minibook.domain.usecase.transaction.ObserveTransactionsUseCase
import com.minibook.testutil.FakeCategoryRepository
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
 * TransactionsViewModel 分类筛选（categoryId）单测（分类筛选栏任务，对齐 SearchViewModelAmountRangeTest 风格）。
 *
 * 覆盖：
 * - [TransactionsViewModel.setCategoryFilter] 驱动 [ObserveTransactionsUseCase.observe]
 *   的 filter.categoryId（经 FakeTransactionRepository.lastFilter 记录入参验证）；
 * - 类型 + 分类组合透传 filter 两字段；setCategoryFilter(null) 仅清分类、保留类型；
 * - 空态 [TransactionsUiState.Empty.hasActiveFilter] 在「仅分类筛选」时也为 true；
 * - [TransactionsViewModel.clearFilter] 一并复位类型 + 分类。
 *
 * 说明：ViewModel 依赖 `viewModelScope`（Main 调度器），故用 `kotlinx-coroutines-test` 的
 * [StandardTestDispatcher] + [Dispatchers.setMain] 提供可控 Main 调度器；分类流空即可
 * （本组不验证分类联表，仅验证 filter 透传与空态判定）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelCategoryFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- setCategoryFilter 驱动 observe filter ----

    @Test
    fun `setCategoryFilter 驱动 observe filter 的 categoryId`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)
        // 初始：无筛选 → categoryId 为 null
        assertEquals(null, txRepo.lastFilter?.categoryId)

        vm.setCategoryFilter("cat-food")
        advanceTimeBy(1)
        assertEquals("cat-food", txRepo.lastFilter?.categoryId)
    }

    @Test
    fun `类型与分类筛选组合透传 filter 两字段`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)

        vm.setFilter(TransactionType.EXPENSE)
        vm.setCategoryFilter("cat-food")
        advanceTimeBy(1)
        assertEquals(TransactionType.EXPENSE, txRepo.lastFilter?.type)
        assertEquals("cat-food", txRepo.lastFilter?.categoryId)
    }

    @Test
    fun `setCategoryFilter null 清空分类但保留类型筛选`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)

        vm.setFilter(TransactionType.EXPENSE)
        vm.setCategoryFilter("cat-food")
        advanceTimeBy(1)
        vm.setCategoryFilter(null)
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.categoryId)
        assertEquals(TransactionType.EXPENSE, txRepo.lastFilter?.type)
    }

    // ---- 空态 hasActiveFilter ----

    @Test
    fun `仅分类筛选命中时空态 hasActiveFilter 为 true`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository() // transactions 默认空 → Empty
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)
        // 初始无筛选：Empty(hasActiveFilter=false)
        assertTrue(vm.uiState.value is TransactionsUiState.Empty)
        assertFalse((vm.uiState.value as TransactionsUiState.Empty).hasActiveFilter)

        vm.setCategoryFilter("cat-food")
        advanceTimeBy(1)
        val state = vm.uiState.value as TransactionsUiState.Empty
        assertTrue("仅分类筛选也应视为活跃筛选", state.hasActiveFilter)
    }

    // ---- clearFilter ----

    @Test
    fun `clearFilter 一并复位类型与分类筛选`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)

        vm.setFilter(TransactionType.INCOME)
        vm.setCategoryFilter("cat-salary")
        advanceTimeBy(1)
        vm.clearFilter()
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.type)
        assertEquals(null, txRepo.lastFilter?.categoryId)
    }

    // ---- 构造 ----

    /** 以已设置当前账本的 LedgerDataStore 构造 VM，保证 observe 路径触达仓库（记录 filter 入参）。 */
    private suspend fun newViewModel(txRepo: FakeTransactionRepository): TransactionsViewModel {
        val ledgerStore = fakeLedgerDataStore("ledger-1")
        return TransactionsViewModel(
            observeTransactions = ObserveTransactionsUseCase(
                transactionRepository = txRepo,
                ledgerDataStore = ledgerStore,
            ),
            observeCategories = ObserveCategoriesUseCase(
                ledgerDataStore = ledgerStore,
                categoryRepository = FakeCategoryRepository(),
            ),
            deleteTransactions = DeleteTransactionsUseCase(transactionRepository = txRepo),
        )
    }

    /** 订阅 [TransactionsViewModel.uiState]，驱动 WhileSubscribed 启动上游观察流。 */
    private fun CoroutineScope.activate(vm: TransactionsViewModel) {
        launch { vm.uiState.collect { } }
    }
}
