package com.minibook.domain.usecase.search

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.SyncStatus
import com.minibook.testutil.FakeDebtRepository
import com.minibook.testutil.fakeLedgerDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SearchDebtsUseCase 单测（TASK-032，PRD F-06 / DEC-013 / A-2）。
 *
 * 覆盖：filter 字段（partyName/kind/direction）透传给
 * [com.minibook.domain.repository.DebtRepository.observeByLedger]；默认
 * DebtFilter()（全 null）等价不过滤；账本未初始化发空列表保活。
 */
class SearchDebtsUseCaseTest {

    @Test
    fun `债务搜索 filter 字段透传`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val repo = FakeDebtRepository()
        val filter = DebtFilter(
            partyName = "张三",
            kind = DebtKind.LEND,
            direction = DebtDirection.OWE_ME,
        )

        useCase(repo, ledger)(filter).first()

        assertEquals("ledger-1", repo.lastObservedLedgerId)
        assertEquals(filter, repo.lastFilter)
        assertEquals("张三", repo.lastFilter?.partyName)
        assertEquals(DebtKind.LEND, repo.lastFilter?.kind)
        assertEquals(DebtDirection.OWE_ME, repo.lastFilter?.direction)
    }

    @Test
    fun `仅姓名关键词过滤非 null 化`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val repo = FakeDebtRepository()
        val filter = DebtFilter(partyName = "李四")

        useCase(repo, ledger)(filter).first()

        assertEquals("李四", repo.lastFilter?.partyName)
        assertNull("未选方向/kind 应为 null", repo.lastFilter?.direction)
        assertNull(repo.lastFilter?.kind)
    }

    @Test
    fun `默认 filter 返回全量（全 null 不过滤）`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val repo = FakeDebtRepository().apply { debts = listOf(debt("debt-1")) }

        val result = useCase(repo, ledger)()
            .first()

        assertEquals(1, result.size)
        assertEquals(DebtFilter(), repo.lastFilter)
    }

    @Test
    fun `当前账本未初始化 - 发空列表保活且不触达 Repository`() = runTest {
        val repo = FakeDebtRepository()
        val result = useCase(repo, fakeLedgerDataStore())(DebtFilter(partyName = "x"))
            .first()

        assertEquals(0, result.size)
        assertNull(repo.lastObservedLedgerId)
        assertNull(repo.lastFilter)
    }

    private fun useCase(repo: FakeDebtRepository, ledger: LedgerDataStore) =
        SearchDebtsUseCase(debtRepository = repo, ledgerDataStore = ledger)

    private fun debt(id: String): Debt = Debt(
        id = id,
        ledgerId = "ledger-1",
        partyName = "张三",
        amount = 5000L,
        kind = DebtKind.LEND,
        direction = DebtDirection.OWE_ME,
        date = "2026-08-18",
        remark = null,
        createdAt = "2026-08-18T06:14:30.123Z",
        updatedAt = "2026-08-18T06:14:30.123Z",
        syncStatus = SyncStatus.LOCAL,
    )
}
