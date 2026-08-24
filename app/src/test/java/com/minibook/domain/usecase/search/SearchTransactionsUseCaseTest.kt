package com.minibook.domain.usecase.search

import com.minibook.domain.model.SyncStatus
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.fakeLedgerDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SearchTransactionsUseCase 单测（TASK-032，PRD F-06 / DEC-013）。
 *
 * 覆盖：filter 字段（remark/amountMin/amountMax/categoryId/dateFrom/dateTo/type）
 * 透传给 [com.minibook.domain.repository.TransactionRepository.observeByLedger]；
 * 默认 filter=TransactionFilter()（全 null）等价不过滤；账本未初始化发空列表保活。
 *
 * 观察路径直注 [com.minibook.data.local.datastore.LedgerDataStore]（fake），
 * 以 `currentLedgerId` 流做 `flatMapLatest`（切账本重订阅）。
 */
class SearchTransactionsUseCaseTest {

    @Test
    fun `交易搜索 filter 字段透传`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val repo = FakeTransactionRepository()
        val filter = TransactionFilter(
            remark = "午饭",
            amountMin = 1000L,
            amountMax = 5000L,
            categoryId = "cat-food",
            dateFrom = "2026-08-01",
            dateTo = "2026-08-31",
            type = TransactionType.EXPENSE,
        )

        useCase(repo, ledger)(filter).first()

        assertEquals("ledger-1", repo.lastObservedLedgerId)
        // 透传逐字段：remark / 金额区间（分、含端点）/ 分类 / 日期区间 / 类型
        assertEquals(filter, repo.lastFilter)
        assertEquals("午饭", repo.lastFilter?.remark)
        assertEquals(1000L, repo.lastFilter?.amountMin)
        assertEquals(5000L, repo.lastFilter?.amountMax)
        assertEquals("cat-food", repo.lastFilter?.categoryId)
        assertEquals("2026-08-01", repo.lastFilter?.dateFrom)
        assertEquals("2026-08-31", repo.lastFilter?.dateTo)
        assertEquals(TransactionType.EXPENSE, repo.lastFilter?.type)
    }

    @Test
    fun `默认 filter 返回全量（全 null 不过滤）`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val repo = FakeTransactionRepository().apply {
            transactions = listOf(tx("tx-1"))
        }

        val result = useCase(repo, ledger)()
            .first()

        assertEquals(1, result.size)
        // 默认 TransactionFilter() 全 null 透传
        assertEquals(TransactionFilter(), repo.lastFilter)
        assertNull(repo.lastFilter?.remark)
        assertNull(repo.lastFilter?.amountMin)
        assertNull(repo.lastFilter?.amountMax)
    }

    @Test
    fun `当前账本未初始化 - 发空列表保活且不触达 Repository`() = runTest {
        val repo = FakeTransactionRepository()
        val result = useCase(repo, fakeLedgerDataStore())(TransactionFilter(remark = "x"))
            .first()

        assertEquals(0, result.size)
        assertNull("空串账本不应触达 Repository", repo.lastObservedLedgerId)
        assertNull(repo.lastFilter)
    }

    private fun useCase(repo: FakeTransactionRepository, ledger: LedgerDataStore) =
        SearchTransactionsUseCase(transactionRepository = repo, ledgerDataStore = ledger)

    private fun tx(id: String): Transaction = Transaction(
        id = id,
        ledgerId = "ledger-1",
        type = TransactionType.EXPENSE,
        amount = 100L,
        categoryId = "cat-1",
        description = null,
        date = "2026-08-18",
        createdAt = "2026-08-18T06:14:30.123Z",
        updatedAt = "2026-08-18T06:14:30.123Z",
        syncStatus = SyncStatus.LOCAL,
    )
}
