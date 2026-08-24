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

    /**
     * 级联删除该分类的全部分类预算行（P1-1）。
     *
     * 删除分类（DeleteCategoryUseCase / DeleteCategoryWithMigrationUseCase）时，于同一
     * 挂起事务内调用本方法清空 `categoryId` 命中的预算行，防分类删除后其预算在
     * `budgets` 表成孤儿、从预算进度静默消失（P1-1）。分类 id 为全局唯一 UUID，
     * `WHERE categoryId = :categoryId` 命中该分类跨所有月份的分类预算；月度总预算
     * （categoryId IS NULL）不受影响。原子性由调用方
     * [com.minibook.data.datasource.TransactionRunner.runInTransaction] 保证。
     */
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: String)
}