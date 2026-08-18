package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minibook.data.local.db.entity.LedgerEntity
import kotlinx.coroutines.flow.Flow

/**
 * 账本 DAO，严格对齐 DATABASE §6.4。
 *
 * 账本无 `ledgerId` 过滤（其自身即根作用域）；默认账本查 `isDefault=1`（§4.4：
 * 至少一个默认账本，首次 DB 打开由 Bootstrap 引导创建）。
 */
@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(ledger: LedgerEntity)

    /** 账本列表：position 升序、同序按创建时间。 */
    @Query("SELECT * FROM ledgers ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledgers WHERE isDefault=1 LIMIT 1")
    suspend fun getDefault(): LedgerEntity?

    @Query("SELECT * FROM ledgers WHERE id=:id")
    suspend fun getById(id: String): LedgerEntity?

    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun count(): Long
}