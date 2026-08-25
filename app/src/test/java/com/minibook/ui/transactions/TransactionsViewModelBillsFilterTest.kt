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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [TransactionsViewModel.applyBillsFilter] 单测。
 *
 * 报表分类占比点击 → 账单筛选请求由 [TransactionsScreen] 组合作用域消费后调用本方法（见时机说明），
 * 此处直接断言方法赋值的副作用：
 * - setFilter(type) / setCategoryFilter(categoryId) / setDateRange(dateFrom, dateTo)；
 * - 备注清空（查询框即跳转落点，无独立提示条）；
 * - 不在此 clear 总线（由 Screen 负责）。
 *
 * 按现有 TransactionsViewModelCategoryFilterTest 风格：类级 StandardTestDispatcher 在
 * `runTest(dispatcher)` 前 `Dispatchers.setMain`，使 viewModelScope（Main）与 runTest 同调度器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelBillsFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `applyBillsFilter 设置 type category 日期区间并清空备注`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceUntilIdle()
        assertNull(txRepo.lastFilter?.type)
        assertNull(txRepo.lastFilter?.dateFrom)

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
        advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, txRepo.lastFilter?.type)
        assertEquals("cat-food", txRepo.lastFilter?.categoryId)
        assertEquals("2026-08-01", txRepo.lastFilter?.dateFrom)
        assertEquals("2026-08-31", txRepo.lastFilter?.dateTo)
        assertNull(vm.remark.value)
    }

    @Test
    fun `clearFilter 一并复位类型分类日期区间与备注`() = runTest(dispatcher) {
        val txRepo = FakeTransactionRepository()
        val vm = newViewModel(txRepo)
        backgroundScope.activate(vm)
        advanceUntilIdle()

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
        advanceUntilIdle()
        assertEquals("2026-08-01", txRepo.lastFilter?.dateFrom)

        vm.clearFilter()
        advanceUntilIdle()

        assertNull(txRepo.lastFilter?.type)
        assertNull(txRepo.lastFilter?.categoryId)
        assertNull(txRepo.lastFilter?.dateFrom)
        assertNull(txRepo.lastFilter?.dateTo)
        assertNull(vm.remark.value)
    }

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