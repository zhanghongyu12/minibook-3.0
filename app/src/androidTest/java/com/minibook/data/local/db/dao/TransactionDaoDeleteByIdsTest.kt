// 需 Android 设备/模拟器运行：./gradlew :app:connectedAndroidTest（暂不执行，待 Stage 9 设备验证）
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批量删除 DAO instrumented 测试（记账页批量管理，`deleteByIds`）。
 *
 * 用 Room in-memory DB 验证 `deleteByIds(ids)`：`id IN (:ids)` 精确删除指定行、返回
 * 实际删除行数；未命中 id 不影响其它行（UUID 全局唯一，不限定 ledgerId）。空列表在
 * UseCase 层短路，本测试只覆盖非空列表路径。
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoDeleteByIdsTest {

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

    private fun insertTx(id: String, ledgerId: String = LEDGER) = runBlocking {
        txDao.insert(
            TransactionEntity(
                id = id,
                ledgerId = ledgerId,
                type = "expense",
                amount = 1000L,
                categoryId = "cat-1",
                description = null,
                date = "2026-08-18",
                createdAt = "2026-08-18T00:00:00.000Z",
                updatedAt = "2026-08-18T00:00:00.000Z",
            ),
        )
    }

    private fun remainingIds(): List<String> =
        runBlocking { txDao.observeAll(LEDGER).first() }.map { it.id }

    @Test
    /** deleteByIds 精确删除指定 id、返回删除行数，未命中 id 不影响其它行。 */
    fun deleteByIdsDeletesOnlySpecifiedAndReturnsCount() {
        insertTx("t1")
        insertTx("t2")
        insertTx("t3")
        insertTx("t4", ledgerId = "ledger-other") // 跨账本行：按 id 删除语义下不受影响

        val deleted = runBlocking { txDao.deleteByIds(listOf("t1", "t3", "t-missing")) }

        assertEquals(2, deleted)
        assertEquals(listOf("t2"), remainingIds())
    }

    @Test
    /** 全部未命中 → 返回 0，数据不变。 */
    fun deleteByIdsNoMatchReturnsZero() {
        insertTx("t1")
        insertTx("t2")

        val deleted = runBlocking { txDao.deleteByIds(listOf("t-missing-1", "t-missing-2")) }

        assertEquals(0, deleted)
        assertEquals(2, remainingIds().size)
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}
