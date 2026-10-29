package com.minibook.domain.usecase.debt

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.SyncStatus
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.testutil.FakeDebtRepository
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.FakeTransactionRunner
import com.minibook.testutil.fakeLedgerDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 账单转债务关联（DEC-056）UseCase 单测：落债务 + 置位账单 `debtId` 同一事务、删债务解除关联、
 * 反查「账单转入」集合。
 *
 * 说明：JVM 单测里 [FakeTransactionRunner] 不开真事务（不模拟回滚），因此「账单已删 → 整体回滚
 * 不留孤儿债务」只断言到「抛 [InvalidInput]」；事务原子性本身由 androidTest 的
 * `TransactionRunnerAtomicityTest` 与 Room `withTransaction` 保证。
 */
class BillToDebtLinkUseCaseTest {

    private suspend fun ledgerProvider() = LedgerProvider(fakeLedgerDataStore(LEDGER))

    private fun tx(id: String, debtId: String? = null) = Transaction(
        id = id,
        ledgerId = LEDGER,
        type = TransactionType.EXPENSE,
        amount = 12800L,
        categoryId = "cat-1",
        description = "聚餐",
        date = "2026-10-20",
        createdAt = "2026-10-20T10:00:00.000Z",
        updatedAt = "2026-10-20T10:00:00.000Z",
        syncStatus = SyncStatus.LOCAL,
        debtId = debtId,
    )

    private fun debt(id: String, txLinked: Boolean = true) = Debt(
        id = id,
        ledgerId = LEDGER,
        partyName = "张三",
        amount = 12800L,
        kind = DebtKind.LEND,
        direction = DebtDirection.OWE_ME,
        date = "2026-10-20",
        remark = if (txLinked) "由账单转入" else null,
        createdAt = "2026-10-20T10:00:00.000Z",
        updatedAt = "2026-10-20T10:00:00.000Z",
    )

    // ==== AddDebtUseCase ====

    @Test
    /** 带 linkTransactionId：落债务后把该账单 debtId 置为新债务 id，且整体包在一个事务里。 */
    fun addDebtWithLinkSetsTransactionDebtId() = runTest {
        val debtRepo = FakeDebtRepository().apply { insertResultId = "debt-new" }
        val txRepo = FakeTransactionRepository().apply { transactionsById = mapOf("tx-1" to tx("tx-1")) }
        val runner = FakeTransactionRunner()
        val useCase = AddDebtUseCase(debtRepo, ledgerProvider(), txRepo, runner)

        val debtId = useCase(
            partyName = "张三",
            amount = 12800L,
            kind = DebtKind.LEND,
            direction = DebtDirection.OWE_ME,
            date = "2026-10-20",
            remark = "由账单转入",
            linkTransactionId = "tx-1",
        )

        assertEquals("debt-new", debtId)
        assertEquals(1, debtRepo.insertedDebts.size)
        assertEquals(LEDGER, debtRepo.insertedDebts.single().ledgerId)
        // 关联落在账单侧：debtId == 新债务 id
        assertEquals(Triple(LEDGER, "tx-1", "debt-new"), txRepo.lastSetDebtId)
        assertEquals("debt-new", txRepo.transactionsById["tx-1"]?.debtId)
        assertEquals("落债务与建立关联必须同一事务", 1, runner.transactionsRun)
    }

    @Test
    /** 不带 linkTransactionId（普通记债务）：不触碰交易表。 */
    fun addDebtWithoutLinkDoesNotTouchTransactions() = runTest {
        val debtRepo = FakeDebtRepository().apply { insertResultId = "debt-plain" }
        val txRepo = FakeTransactionRepository()
        val runner = FakeTransactionRunner()
        val useCase = AddDebtUseCase(debtRepo, ledgerProvider(), txRepo, runner)

        useCase(
            partyName = "李四",
            amount = 5000L,
            kind = DebtKind.LEND,
            direction = DebtDirection.I_OWE,
            date = "2026-10-21",
        )

        assertEquals(1, debtRepo.insertedDebts.size)
        assertNull(txRepo.lastSetDebtId)
    }

    @Test
    /** 关联的账单已不存在（setDebtId 影响 0 行）→ 抛 InvalidInput，不静默留孤儿债务。 */
    fun addDebtWithMissingTransactionThrows() = runTest {
        val debtRepo = FakeDebtRepository().apply { insertResultId = "debt-new" }
        val txRepo = FakeTransactionRepository() // 空 map ⇒ setDebtId 返回 0
        val runner = FakeTransactionRunner()
        val useCase = AddDebtUseCase(debtRepo, ledgerProvider(), txRepo, runner)

        try {
            useCase(
                partyName = "张三",
                amount = 12800L,
                kind = DebtKind.LEND,
                direction = DebtDirection.OWE_ME,
                date = "2026-10-20",
                linkTransactionId = "tx-missing",
            )
            fail("账单已删时必须抛 InvalidInput，而不是留下无关联的债务")
        } catch (e: InvalidInput) {
            assertTrue(e.message.orEmpty().contains("账单"))
        }
    }

    @Test
    /** 金额/姓名校验先于落库：非法输入不产生任何写入。 */
    fun addDebtRejectsInvalidInputBeforeInsert() = runTest {
        val debtRepo = FakeDebtRepository()
        val txRepo = FakeTransactionRepository()
        val useCase = AddDebtUseCase(debtRepo, ledgerProvider(), txRepo, FakeTransactionRunner())

        try {
            useCase("", 12800L, DebtKind.LEND, DebtDirection.OWE_ME, "2026-10-20", linkTransactionId = "tx-1")
            fail("空姓名应被拒绝")
        } catch (_: InvalidInput) {
            assertTrue(debtRepo.insertedDebts.isEmpty())
        }
    }

    // ==== DeleteDebtUseCase ====

    @Test
    /** 删债务：删行 + 把由它转来的账单 debtId 清回 NULL（统计随 `debtId IS NULL` 恢复计入）。 */
    fun deleteDebtClearsTransactionLink() = runTest {
        val debtRepo = FakeDebtRepository().apply { debts = listOf(debt("debt-1")) }
        val txRepo = FakeTransactionRepository().apply {
            transactionsById = mapOf("tx-1" to tx("tx-1", debtId = "debt-1"))
        }
        val runner = FakeTransactionRunner()
        val useCase = DeleteDebtUseCase(debtRepo, ledgerProvider(), txRepo, runner)

        useCase("debt-1")

        assertEquals(listOf("debt-1"), debtRepo.deletedIds)
        assertEquals(LEDGER to "debt-1", txRepo.lastClearedDebtId)
        assertNull("删除债务后账单必须恢复为未转状态", txRepo.transactionsById["tx-1"]?.debtId)
        assertEquals("删债务与解除关联必须同一事务", 1, runner.transactionsRun)
    }

    @Test
    /** 删除普通债务（非账单转入）：清关联影响 0 行，不报错。 */
    fun deletePlainDebtIsNoOpOnTransactions() = runTest {
        val debtRepo = FakeDebtRepository().apply { debts = listOf(debt("debt-2", txLinked = false)) }
        val txRepo = FakeTransactionRepository()
        val useCase = DeleteDebtUseCase(debtRepo, ledgerProvider(), txRepo, FakeTransactionRunner())

        useCase("debt-2")

        assertEquals(listOf("debt-2"), debtRepo.deletedIds)
        assertEquals(0, txRepo.transactionsById.size)
    }

    @Test
    /** 当前账本内不存在该债务 → InvalidInput，且不产生任何删除。 */
    fun deleteMissingDebtThrows() = runTest {
        val debtRepo = FakeDebtRepository() // 空列表
        val txRepo = FakeTransactionRepository()
        val useCase = DeleteDebtUseCase(debtRepo, ledgerProvider(), txRepo, FakeTransactionRunner())

        try {
            useCase("debt-404")
            fail("不存在的债务应被拒绝")
        } catch (_: InvalidInput) {
            assertTrue(debtRepo.deletedIds.isEmpty())
            assertNull(txRepo.lastClearedDebtId)
        }
    }

    // ==== ObserveLinkedDebtIdsUseCase ====

    @Test
    /** 「账单转入」集合 = 当前账本内所有非空 debtId（债务侧标记的反查来源）。 */
    fun observeLinkedDebtIdsReturnsLinkedIds() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            transactionsById = mapOf(
                "tx-1" to tx("tx-1", debtId = "debt-1"),
                "tx-2" to tx("tx-2"),
                "tx-3" to tx("tx-3", debtId = "debt-3"),
                "tx-other" to tx("tx-4", debtId = "debt-x").copy(ledgerId = "ledger-other"),
            )
        }
        val useCase = ObserveLinkedDebtIdsUseCase(txRepo, fakeLedgerDataStore(LEDGER))

        assertEquals(setOf("debt-1", "debt-3"), useCase.observe().first())
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}
