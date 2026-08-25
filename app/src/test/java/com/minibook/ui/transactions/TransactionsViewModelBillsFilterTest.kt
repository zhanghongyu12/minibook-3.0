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
import com.minibook.ui.navigation.BillsFilterBus
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TransactionsViewModel 账单筛选跳转总线（[BillsFilterBus]）消费单测。
 *
 * 覆盖：VM init collect [BillsFilterBus.request]，收到非空请求后
 * - setFilter(type) / setCategoryFilter(categoryId)；
 * - 设 dateFrom / dateTo / billsFilterLabel（`周期 · 分类名`）；
 * - 消费后 clear 总线（request 复位 null）；[TransactionsViewModel.clearFilter] 一并复位
 *   类型/分类/日期区间/提示条。
 *
 * 说明：在 `runTest(dispatcher)` 前 `Dispatchers.setMain`，使 viewModelScope（Main）与 runTest
 * 同调度器。由于 [BillsFilterBus] 为进程内单例且 VM 的 init collect 随 viewModelScope 常驻，
 * 单测试方法构造一个 VM 走完整「post → 消费 → clearFilter」链路，避免多用例间订阅单例总线的
 * VM 相互抢消费导致的不确定性；消费路径经 [FakeTransactionRepository] 记录的 filter 入参验证。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelBillsFilterTest {

    @Test
    fun `消费 BillsFilterBus 设置筛选并在 clearFilter 后复位`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                BillsFilterBus.clear()
                val txRepo = FakeTransactionRepository()
                val vm = newViewModel(txRepo)
                backgroundScope.activate(vm)
                advanceUntilIdle()
                // 初始无筛选
                assertNull(txRepo.lastFilter?.type)
                assertNull(txRepo.lastFilter?.dateFrom)

                // 报表分类占比点击 → 投递请求
                BillsFilterBus.post(
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
                assertEquals("2026年8月 · 餐饮", vm.billsFilterLabel.value)
                assertNull("消费后应 clear 总线", BillsFilterBus.request.value)

                // 提示条「清除」→ clearFilter 一并复位类型/分类/日期/提示条
                vm.clearFilter()
                advanceUntilIdle()

                assertNull(txRepo.lastFilter?.type)
                assertNull(txRepo.lastFilter?.categoryId)
                assertNull(txRepo.lastFilter?.dateFrom)
                assertNull(txRepo.lastFilter?.dateTo)
                assertNull(vm.billsFilterLabel.value)
            }
        } finally {
            Dispatchers.resetMain()
        }
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