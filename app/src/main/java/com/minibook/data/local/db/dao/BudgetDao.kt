package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 预算 DAO（Phase 2 建表，本期骨架），对齐 DATABASE §6.5 / DEC-008。
 *
 * `budgets` UNIQUE(ledgerId, month, categoryId) 仅约束分类预算；月度总预算
 * （categoryId=NULL）因 SQLite NULL 互不等而无法被 UNIQUE 保护，业务唯一靠
 * Repository 层「先删后插」保证（[deleteTotal] + [upsert] 成对出现）。
 */
@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE ledgerId=:ledgerId AND month=:month")
    fun observeByMonth(ledgerId: String, month: String): Flow<List<BudgetEntity>>

    /** 删除月度总预算（categoryId IS NULL），供「先删后插」在事务内先执行（DEC-008）。 */
    @Query("DELETE FROM budgets WHERE ledgerId=:ledgerId AND categoryId IS NULL AND month=:month")
    suspend fun deleteTotal(ledgerId: String, month: String)
}