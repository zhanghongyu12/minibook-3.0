package com.minibook.ui.transactions

import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.category.UpsertUserCategoryRuleUseCase
import com.minibook.domain.usecase.transaction.BatchUpdateTransactionsUseCase
import com.minibook.domain.usecase.transaction.DeleteTransactionsUseCase
import com.minibook.domain.usecase.transaction.ObserveTransactionsUseCase
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.FakeUserCategoryRuleRepository
import com.minibook.testutil.fakeLedgerDataStore
import com.minibook.ui.navigation.BillsFilterRequest
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

    // ---- 时间段 / 备注查询态 ----

    @Test
    fun `setRemark 驱动 observe filter remark 且空白归一为 null`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.remark)

        vm.setRemark("  餐饮  ")
        advanceTimeBy(1)
        assertEquals("餐饮", txRepo.lastFilter?.remark)

        vm.setRemark("   ")
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.remark)
    }

    @Test
    fun `setDateRange 设置时间段并支持单端清空`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)

        vm.setDateRange("2026-08-01", "2026-08-31")
        advanceTimeBy(1)
        assertEquals("2026-08-01", txRepo.lastFilter?.dateFrom)
        assertEquals("2026-08-31", txRepo.lastFilter?.dateTo)

        vm.setDateRange(null, "2026-08-31")
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.dateFrom)
        assertEquals("2026-08-31", txRepo.lastFilter?.dateTo)

        vm.setDateRange("2026-08-01", null)
        advanceTimeBy(1)
        assertEquals("2026-08-01", txRepo.lastFilter?.dateFrom)
        assertEquals(null, txRepo.lastFilter?.dateTo)
    }

    @Test
    fun `clearFilter 复位时间段与备注`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)

        vm.setFilter(TransactionType.EXPENSE)
        vm.setCategoryFilter("cat-food")
        vm.setDateRange("2026-08-01", "2026-08-31")
        vm.setRemark("餐饮")
        advanceTimeBy(1)
        vm.clearFilter()
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.type)
        assertEquals(null, txRepo.lastFilter?.categoryId)
        assertEquals(null, txRepo.lastFilter?.dateFrom)
        assertEquals(null, txRepo.lastFilter?.dateTo)
        assertEquals(null, txRepo.lastFilter?.remark)
    }

    @Test
    fun `applyBillsFilter 可空填入 type 与 categoryId`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceTimeBy(1)

        // 全空口径：type/categoryId 为 null，日期区间照填，备注清空。
        vm.applyBillsFilter(
            BillsFilterRequest(
                categoryId = null,
                categoryName = null,
                type = null,
                dateFrom = "2026-08-01",
                dateTo = "2026-08-31",
                periodLabel = "2026年8月",
            ),
        )
        advanceTimeBy(1)
        assertEquals(null, txRepo.lastFilter?.type)
        assertEquals(null, txRepo.lastFilter?.categoryId)
        assertEquals("2026-08-01", txRepo.lastFilter?.dateFrom)
        assertEquals("2026-08-31", txRepo.lastFilter?.dateTo)
        assertEquals(null, txRepo.lastFilter?.remark)

        // 非空口径：type/categoryId 带入查询框。
        vm.applyBillsFilter(
            BillsFilterRequest(
                categoryId = "cat-food",
                categoryName = "餐饮",
                type = TransactionType.EXPENSE,
                dateFrom = "2026-08-01",
                dateTo = "2026-08-31",
                periodLabel = "2026年8月",
            ),
        )
        advanceTimeBy(1)
        assertEquals(TransactionType.EXPENSE, txRepo.lastFilter?.type)
        assertEquals("cat-food", txRepo.lastFilter?.categoryId)
    }

    // ---- deleteTransactions 委托（回归：方法/属性同名曾致无限递归 StackOverflowError）----

    @Test
    fun `deleteTransactions 委托 UseCase 并透传 ids 与返回行数`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository().apply { deleteByIdsResult = 3 }
        val vm = newViewModel(txRepo)

        val deleted = vm.deleteTransactions(listOf("t1", "t2", "t3"))

        assertEquals(listOf("t1", "t2", "t3"), txRepo.lastDeletedIds)
        assertEquals(3, deleted)
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
            deleteTransactionsUseCase = DeleteTransactionsUseCase(transactionRepository = txRepo),
            batchUpdateTransactions = BatchUpdateTransactionsUseCase(
                transactionRepository = txRepo,
                categoryRepository = FakeCategoryRepository(),
                upsertUserCategoryRule = UpsertUserCategoryRuleUseCase(FakeUserCategoryRuleRepository()),
            ),
            ledgerProvider = LedgerProvider(ledgerStore),
        )
    }

    /** 订阅 [TransactionsViewModel.uiState]，驱动 WhileSubscribed 启动上游观察流。 */
    private fun CoroutineScope.activate(vm: TransactionsViewModel) {
        launch { vm.uiState.collect { } }
    }
}
