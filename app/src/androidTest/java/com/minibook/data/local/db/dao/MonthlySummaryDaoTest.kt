// 需 Android 设备/模拟器运行：./gradlew :app:connectedAndroidTest（暂不执行，待 Stage 9 设备验证）
package com.minibook.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minibook.data.local.db.MiniBookDatabase
import com.minibook.data.local.db.SummaryRow
import com.minibook.data.local.db.entity.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 月结余 DAO SQL 汇总 instrumented 测试（DATABASE §6.1 monthSummary / §4.1 注）。
 *
 * 用 Room in-memory DB 验证 `monthSummary(ledgerId, monthStart, monthEnd)` 按日期区间
 * （BETWEEN，含端点）汇总 expense/income，并跨月/跨账本过滤正确。
 */
@RunWith(AndroidJUnit4::class)
class MonthlySummaryDaoTest {

    private lateinit var db: MiniBookDatabase
    private lateinit var txDao: TransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiniBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        txDao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertTx(id: String, type: String, amount: Long, date: String) = runBlocking {
        txDao.insert(
            TransactionEntity(
                id = id,
                ledgerId = LEDGER,
                type = type,
                amount = amount,
                categoryId = "cat-1",
                description = null,
                date = date,
                createdAt = "2026-01-01T00:00:00.000Z",
                updatedAt = "2026-01-01T00:00:00.000Z",
            ),
        )
    }

    private fun summary(monthStart: String, monthEnd: String): SummaryRow =
        runBlocking { txDao.monthSummary(LEDGER, monthStart, monthEnd) }

    @Test
    fun `monthSummary 按区间汇总收支`() {
        insertTx("t1", "expense", 1000L, "2026-03-05")
        insertTx("t2", "income", 500L, "2026-03-20")
        insertTx("t3", "expense", 200L, "2026-03-31")   // 区间端点含入
        insertTx("t4", "expense", 9999L, "2026-04-01")  // 跨月应排除
        val row = summary("2026-03-01", "2026-03-31")
        assertEquals(1200L, row.expenseFen)
        assertEquals(500L, row.incomeFen)
        assertEquals(-700L, row.balance) // balance = income - expense
    }

    @Test
    fun `monthSummary 空月全零`() {
        val row = summary("2026-02-01", "2026-02-28")
        assertEquals(0L, row.expenseFen)
        assertEquals(0L, row.incomeFen)
        assertEquals(0L, row.balance)
    }

    @Test
    fun `monthSummary 仅统计指定账本`() {
        insertTx("t1", "expense", 1000L, "2026-03-05")
        runBlocking {
            txDao.insert(
                TransactionEntity(
                    id = "t2",
                    ledgerId = "ledger-other",
                    type = "expense",
                    amount = 2000L,
                    categoryId = "cat-1",
                    description = null,
                    date = "2026-03-06",
                    createdAt = "2026-01-01T00:00:00.000Z",
                    updatedAt = "2026-01-01T00:00:00.000Z",
                ),
            )
        }
        val row = runBlocking { txDao.monthSummary("ledger-other", "2026-03-01", "2026-03-31") }
        assertEquals(2000L, row.expenseFen)
        assertEquals(0L, row.incomeFen)
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}
