// 需 Android 设备/模拟器运行：./gradlew :app:connectedAndroidTest（暂不执行，待 Stage 9 设备验证）
package com.minibook.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minibook.data.local.db.MiniBookDatabase
import com.minibook.data.local.db.NetByPartyRow
import com.minibook.data.local.db.entity.DebtEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 债务净额 DAO 口径 instrumented 测试（PRD F-02 / DATABASE §6.2 Q-D2 / API §4）。
 *
 * 用 Room in-memory DB（room-testing 提供 `Room.inMemoryDatabaseBuilder`），不依赖
 * SQLCipher/DatabaseGate/Hilt，直接 new 内存库 + DAO 验证净额 CASE WHEN 口径：
 * lend×owe_me=+、lend×i_owe=−、repay×owe_me=−、repay×i_owe=+。
 */
@RunWith(AndroidJUnit4::class)
class NetByPartyDaoTest {

    private lateinit var db: MiniBookDatabase
    private lateinit var debtDao: DebtDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiniBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        debtDao = db.debtDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertDebt(
        id: String,
        partyName: String,
        amount: Long,
        kind: String,
        direction: String,
    ) = runBlocking {
        debtDao.insert(
            DebtEntity(
                id = id,
                ledgerId = LEDGER,
                partyName = partyName,
                amount = amount,
                kind = kind,
                direction = direction,
                date = "2026-08-18",
                remark = null,
                createdAt = "2026-08-18T00:00:00.000Z",
                updatedAt = "2026-08-18T00:00:00.000Z",
                syncStatus = "local",
            ),
        )
    }

    private fun netByParty(): List<NetByPartyRow> =
        runBlocking { debtDao.observeNetByParty(LEDGER).first() }

    @Test
    /** lend×owe_me 净额为正（借出、对方欠我 → +）。 */
    fun lendOweMeNetPositive() {
        insertDebt("d1", "张三", 10_000L, "lend", "owe_me")
        assertEquals(listOf(NetByPartyRow("张三", 10_000L)), netByParty())
    }

    @Test
    /** lend×i_owe 净额为负（借入、我欠对方 → −）。 */
    fun lendIOweNetNegative() {
        insertDebt("d2", "张三", 10_000L, "lend", "i_owe")
        assertEquals(listOf(NetByPartyRow("张三", -10_000L)), netByParty())
    }

    @Test
    /** repay×owe_me 净额为负（收到还款、对方欠我 → −）。 */
    fun repayOweMeNetNegative() {
        insertDebt("d3", "张三", 10_000L, "repay", "owe_me")
        assertEquals(listOf(NetByPartyRow("张三", -10_000L)), netByParty())
    }

    @Test
    /** repay×i_owe 净额为正（我还款、我欠对方 → +）。 */
    fun repayIOweNetPositive() {
        insertDebt("d4", "张三", 10_000L, "repay", "i_owe")
        assertEquals(listOf(NetByPartyRow("张三", 10_000L)), netByParty())
    }

    @Test
    /** 同对象多笔（lend/repay 混合）聚合求和。 */
    fun samePartyMultipleDebtsAggregated() {
        insertDebt("d5", "张三", 10_000L, "lend", "owe_me")   // +10000
        insertDebt("d6", "张三", 5_000L, "lend", "owe_me")    // +5000
        insertDebt("d7", "张三", 3_000L, "repay", "owe_me")   // -3000
        insertDebt("d8", "李四", 20_000L, "lend", "i_owe")    // -20000
        insertDebt("d9", "李四", 3_000L, "repay", "i_owe")    // +3000
        val net = netByParty()
        assertEquals(2, net.size)
        assertEquals(12_000L, net.first { it.partyName == "张三" }.netFen)
        assertEquals(-17_000L, net.first { it.partyName == "李四" }.netFen)
    }

    @Test
    /** 仅统计指定账本：跨账本债务不混入。 */
    fun onlyCountsSpecifiedLedger() {
        insertDebt("d10", "张三", 10_000L, "lend", "owe_me")
        runBlocking {
            debtDao.insert(
                DebtEntity(
                    id = "d11",
                    ledgerId = "ledger-other",
                    partyName = "王五",
                    amount = 99_000L,
                    kind = "lend",
                    direction = "owe_me",
                    date = "2026-08-18",
                    remark = null,
                    createdAt = "2026-08-18T00:00:00.000Z",
                    updatedAt = "2026-08-18T00:00:00.000Z",
                ),
            )
        }
        val net = netByParty()
        assertEquals(1, net.size)
        assertEquals(NetByPartyRow("张三", 10_000L), net.first())
    }

    @Test
    /** 无数据返回空列表。 */
    fun returnsEmptyListWhenNoData() {
        assertTrue(netByParty().isEmpty())
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}
