// 需 Android 设备/模拟器运行：./gradlew :app:connectedAndroidTest（暂不执行，待 Stage 9 设备验证）
package com.minibook.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minibook.data.local.datasource.RoomDataSourceImpl
import com.minibook.data.local.datasource.RoomTransactionRunner
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.security.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 迁移事务原子性 instrumented 测试（TASK-009 / TASK-015，ARCHITECTURE §7.5）。
 *
 * 可测性改造（commit 3f042b6）后，[RoomTransactionRunner] 与 [RoomDataSourceImpl] 均仅依赖
 * [DatabaseProvider] 接口（`val database: MiniBookDatabase?`），因此可用 Room 内存库 + 匿名
 * DatabaseProvider 替身直接构造被测对象，无需 SQLCipher/KeyManager/Hilt（无 hilt-testing）。
 *
 * 验证点：
 * 1. [RoomDataSourceImpl.deleteById]：transactions/debts/categories 三表按 id 分发删除，
 *    整体包进事务（自身原子，TASK-013 评审 W4）；事务内中途抛异常 → 整体回滚。
 * 2. [RoomTransactionRunner.runInTransaction]：block 内抛异常 → 整体回滚、异常上抛。
 * 3. [RoomDataSourceImpl.deleteByLedger]：transactions/debts/categories/budgets 四表按
 *    ledgerId 批删，整体包进事务（自身原子）；无 `isBuiltIn=0` 守卫（内置分类一并清空）。
 */
@RunWith(AndroidJUnit4::class)
class TransactionRunnerAtomicityTest {

    private lateinit var db: MiniBookDatabase
    private lateinit var runner: RoomTransactionRunner
    private lateinit var ds: RoomDataSourceImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiniBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val provider = object : DatabaseProvider {
            override val database: MiniBookDatabase get() = db
        }
        runner = RoomTransactionRunner(provider)
        ds = RoomDataSourceImpl(provider, runner)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `deleteById 三表分发原子性`() {
        val id = "shared-1"
        runBlocking {
            db.transactionDao().insert(txEntity(id = id))
            db.debtDao().insert(debtEntity(id = id))
            db.categoryDao().insert(categoryEntity(id = id, isBuiltIn = false))
        }
        // 前置：三表各命中该 id 一行。
        assertTrue(transactionIds().contains(id))
        assertTrue(debtIds().contains(id))
        assertTrue(categoryIds().contains(id))

        runBlocking { ds.deleteById(id) }

        // 三表该 id 均不存在（categories 行 isBuiltIn=0 才能被删）。
        assertFalse(transactionIds().contains(id))
        assertFalse(debtIds().contains(id))
        assertFalse(categoryIds().contains(id))

        // 回滚语义：事务内插行后抛异常 → 整体回滚、该行未落库。
        val boomId = "boom-tx-1"
        runBlocking {
            var thrown = false
            try {
                runner.runInTransaction<Unit> {
                    db.transactionDao().insert(txEntity(id = boomId))
                    throw RuntimeException("boom")
                }
            } catch (_: RuntimeException) {
                thrown = true
            }
            assertTrue("异常应原样上抛", thrown)
        }
        assertFalse("回滚后插入应不存在", transactionIds().contains(boomId))
    }

    @Test
    fun `runInTransaction 内抛异常整体回滚`() {
        val id = "rollback-tx-1"
        runBlocking {
            var thrown = false
            try {
                runner.runInTransaction<Unit> {
                    db.transactionDao().insert(txEntity(id = id))
                    throw RuntimeException("boom")
                }
            } catch (_: RuntimeException) {
                thrown = true
            }
            assertTrue("异常应原样上抛", thrown)
        }
        // 回滚生效：插入的 transaction 不存在。
        assertFalse("runInTransaction 抛异常应整体回滚", transactionIds().contains(id))
    }

    @Test
    fun `deleteByLedger 四表批删原子性`() {
        val ledger = "ledger-del"
        runBlocking {
            db.transactionDao().insert(txEntity(id = "tx-l", ledgerId = ledger))
            db.debtDao().insert(debtEntity(id = "debt-l", ledgerId = ledger))
            // 内置分类（isBuiltIn=true）也须被批删清空（deleteByLedger 无 isBuiltIn 守卫）。
            db.categoryDao().insert(categoryEntity(id = "cat-l", ledgerId = ledger, isBuiltIn = true))
            db.budgetDao().upsert(budgetEntity(id = "budget-l", ledgerId = ledger))
        }
        // 前置：四表各命中 ledger 一行。
        assertTrue(transactionIds(ledger).contains("tx-l"))
        assertTrue(debtIds(ledger).contains("debt-l"))
        assertTrue(categoryIds(ledger).contains("cat-l"))
        assertTrue(budgetIds(ledger).contains("budget-l"))

        runBlocking { ds.deleteByLedger(ledger) }

        // 四表该 ledgerId 均清空。
        assertTrue(transactionIds(ledger).isEmpty())
        assertTrue(debtIds(ledger).isEmpty())
        assertTrue(categoryIds(ledger).isEmpty())
        assertTrue(budgetIds(ledger).isEmpty())

        // 回滚语义：事务内插第五行后抛异常 → 该行回滚、四表仍为空。
        val fifthId = "tx-fifth"
        runBlocking {
            var thrown = false
            try {
                runner.runInTransaction<Unit> {
                    db.transactionDao().insert(txEntity(id = fifthId, ledgerId = ledger))
                    throw RuntimeException("boom")
                }
            } catch (_: RuntimeException) {
                thrown = true
            }
            assertTrue("异常应原样上抛", thrown)
        }
        assertFalse("回滚后第五行应不存在", transactionIds(ledger).contains(fifthId))
        assertTrue("回滚后四表应仍为空", transactionIds(ledger).isEmpty())
    }

    // ===== fixture / 断言辅助 =====

    private fun txEntity(id: String, ledgerId: String = LEDGER) = TransactionEntity(
        id = id,
        ledgerId = ledgerId,
        type = "expense",
        amount = 1000L,
        categoryId = "cat-ref",
        description = null,
        date = "2026-08-18",
        createdAt = "2026-08-18T00:00:00.000Z",
        updatedAt = "2026-08-18T00:00:00.000Z",
        syncStatus = "local",
    )

    private fun debtEntity(id: String, ledgerId: String = LEDGER) = DebtEntity(
        id = id,
        ledgerId = ledgerId,
        partyName = "张三",
        amount = 1000L,
        kind = "lend",
        direction = "owe_me",
        date = "2026-08-18",
        remark = null,
        createdAt = "2026-08-18T00:00:00.000Z",
        updatedAt = "2026-08-18T00:00:00.000Z",
        syncStatus = "local",
    )

    private fun categoryEntity(
        id: String,
        ledgerId: String = LEDGER,
        isBuiltIn: Boolean = false,
    ) = CategoryEntity(
        id = id,
        ledgerId = ledgerId,
        name = "测试分类",
        icon = "🍜",
        color = "#FF6B6B",
        type = "expense",
        isBuiltIn = isBuiltIn,
        order = 1,
        createdAt = "2026-08-18T00:00:00.000Z",
        updatedAt = "2026-08-18T00:00:00.000Z",
    )

    private fun budgetEntity(id: String, ledgerId: String = LEDGER) = BudgetEntity(
        id = id,
        ledgerId = ledgerId,
        month = "2026-08",
        categoryId = null,
        amount = 5000L,
        createdAt = "2026-08-18T00:00:00.000Z",
        updatedAt = "2026-08-18T00:00:00.000Z",
    )

    private fun transactionIds(ledgerId: String = LEDGER): List<String> =
        runBlocking { db.transactionDao().observeAll(ledgerId).first().map { it.id } }

    private fun debtIds(ledgerId: String = LEDGER): List<String> =
        runBlocking { db.debtDao().observeAll(ledgerId).first().map { it.id } }

    private fun categoryIds(ledgerId: String = LEDGER): List<String> =
        runBlocking { db.categoryDao().observeByType(ledgerId, "expense").first().map { it.id } }

    private fun budgetIds(ledgerId: String = LEDGER): List<String> =
        runBlocking { db.budgetDao().observeByMonth(ledgerId, "2026-08").first().map { it.id } }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}