package com.minibook.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minibook.data.local.db.MiniBookDatabase
import com.minibook.data.local.db.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 账单转债务（DEC-056）DAO 层 instrumented 测试：用 Room in-memory DB 验证「已转债务的账单
 * 不进统计」这条口径真的落在 SQL 上，而不只是 UI 过滤。
 *
 * 覆盖：
 * 1. 四个聚合（`monthSummary` / `observeDailyTrend` / `observeMonthlyTrend` / `observeCategoryAgg`）
 *    都带 `debtId IS NULL`，转出的账单金额不参与收支、趋势、分类占比与预算「已用」；
 * 2. `clearDebtIdByDebt`（删债务）把账单清回 NULL 后，金额**重新**进入统计——用户要求的
 *    「删了债务，统计重新统计这条账单」；
 * 3. `setDebtId` 的行数语义（账单不存在返回 0，供 UseCase 回滚判断）；
 * 4. `observeLinkedDebtIds` 只返回本账本内非空 `debtId`（债务侧「账单转入」标记的反查来源）；
 * 5. 交易**列表**查询不受影响（已转债务的账单仍要显示，才能打「已转债务」绿标）。
 *
 * 运行前提：需真机/模拟器（`./gradlew :app:connectedDebugAndroidTest`）；真机 `A1B2C3D4`
 * 装着用户真实数据正式版，装 debug 测试 APK 要卸载 ⇒ 需先获批启动模拟器再跑。
 */
@RunWith(AndroidJUnit4::class)
class TransactionDebtLinkDaoTest {

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

    /** 插一条交易；[debtId] 非空表示该账单已转债务。 */
    private fun insertTx(
        id: String,
        type: String,
        amount: Long,
        date: String,
        debtId: String? = null,
        categoryId: String = "cat-1",
        ledgerId: String = LEDGER,
    ) = runBlocking {
        txDao.insert(
            TransactionEntity(
                id = id,
                ledgerId = ledgerId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                description = null,
                date = date,
                createdAt = "2026-10-01T00:00:00.000Z",
                updatedAt = "2026-10-01T00:00:00.000Z",
                debtId = debtId,
            ),
        )
    }

    @Test
    /** 月结余：已转债务的支出不计入 expenseFen，未转的照常计入。 */
    fun monthSummaryExcludesConvertedBills() = runBlocking {
        insertTx("t-normal", "expense", 1000L, "2026-10-05")
        insertTx("t-converted", "expense", 8000L, "2026-10-06", debtId = "debt-1")
        insertTx("t-income", "income", 500L, "2026-10-07")

        val row = txDao.monthSummary(LEDGER, "2026-10-01", "2026-10-31")

        assertEquals("已转债务的 8000 分不得计入支出", 1000L, row.expenseFen)
        assertEquals(500L, row.incomeFen)
        assertEquals(-500L, row.balance)
    }

    @Test
    /** 日/月趋势与分类占比：三处聚合同样排除已转债务的账单。 */
    fun trendsAndCategoryAggExcludeConvertedBills() = runBlocking {
        insertTx("t-normal", "expense", 1000L, "2026-10-05", categoryId = "cat-food")
        insertTx("t-converted", "expense", 8000L, "2026-10-06", debtId = "debt-1", categoryId = "cat-food")

        val daily = txDao.observeDailyTrend(LEDGER, "2026-10-01", "2026-10-31").first()
        val dailyTotal = daily.sumOf { it.expenseFen }
        assertEquals("日趋势不得包含已转债务的账单", 1000L, dailyTotal)
        assertTrue("已转债务的那天不应出现在日趋势里", daily.none { it.bucket == "2026-10-06" })

        val monthly = txDao.observeMonthlyTrend(LEDGER, "2026-01-01", "2026-12-31").first()
        assertEquals(1000L, monthly.sumOf { it.expenseFen })

        // 预算「已用」同源：分类占比也排除（借出去的钱不是消费，不该占预算）。
        val agg = txDao.observeCategoryAgg(LEDGER, "expense", "2026-10-01", "2026-10-31").first()
        assertEquals(1000L, agg.sumOf { it.totalFen })
        assertEquals(listOf("cat-food"), agg.map { it.categoryId })
    }

    @Test
    /** 删债务（clearDebtIdByDebt）后：金额重新进入统计（统计恢复）。 */
    fun clearDebtIdRestoresStatistics() = runBlocking {
        insertTx("t-converted", "expense", 8000L, "2026-10-06", debtId = "debt-1")
        assertEquals(0L, txDao.monthSummary(LEDGER, "2026-10-01", "2026-10-31").expenseFen)

        val cleared = txDao.clearDebtIdByDebt("debt-1", LEDGER, "2026-10-08T00:00:00.000Z")
        assertEquals("应清掉 1 行关联", 1, cleared)

        assertEquals(
            "删债务后该账单必须重新计入统计",
            8000L,
            txDao.monthSummary(LEDGER, "2026-10-01", "2026-10-31").expenseFen,
        )
    }

    @Test
    /** clearDebtIdByDebt 只影响本账本；跨账本同名债务 id 不被误清。 */
    fun clearDebtIdIsLedgerScoped() = runBlocking {
        insertTx("t-other", "expense", 3000L, "2026-10-06", debtId = "debt-1", ledgerId = "ledger-other")

        val cleared = txDao.clearDebtIdByDebt("debt-1", LEDGER, "2026-10-08T00:00:00.000Z")

        assertEquals(0, cleared)
        val stillLinked = txDao.observeLinkedDebtIds("ledger-other").first()
        assertEquals(listOf("debt-1"), stillLinked)
    }

    @Test
    /** setDebtId 行数语义：命中返 1，账单不存在返 0（UseCase 据此回滚）。 */
    fun setDebtIdReturnsAffectedRows() = runBlocking {
        insertTx("t-1", "expense", 1000L, "2026-10-05")

        assertEquals(1, txDao.setDebtId("t-1", LEDGER, "debt-1", "2026-10-08T00:00:00.000Z"))
        assertEquals(0, txDao.setDebtId("t-404", LEDGER, "debt-1", "2026-10-08T00:00:00.000Z"))
        assertEquals(listOf("debt-1"), txDao.observeLinkedDebtIds(LEDGER).first())
    }

    @Test
    /** 交易列表仍包含已转债务的账单（要显示「已转债务」绿标），只有统计排除。 */
    fun transactionListStillShowsConvertedBills() = runBlocking {
        insertTx("t-converted", "expense", 8000L, "2026-10-06", debtId = "debt-1")

        val list = txDao.observeAll(LEDGER).first()

        assertEquals(1, list.size)
        assertEquals("debt-1", list.single().debtId)
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}
