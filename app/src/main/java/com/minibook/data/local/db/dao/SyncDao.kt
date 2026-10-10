package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.db.entity.UserCategoryRuleEntity

/**
 * 同步引擎专用跨表 DAO（F-15 / DEC-033，`docs/14_cloud_sync.md §4.3 / §4.4 / §5.5`）。
 *
 * **为什么不复用各业务 DAO**：同步需要三类统一口径的批量能力，既有 DAO 都没有——
 * 1. `xxxSince(since)`：按 `updatedAt >= :since` 取待上行集合（`>=` 而非 `>`：同秒多次编辑不漏，
 *    多推一次幂等无害）；
 * 2. `upsertXxx`：pull 落地必须 **REPLACE** 语义（既有 `TransactionDao.insert` 是 ABORT，
 *    云端下发的行撞本地 id 会直接抛异常）；
 * 3. `deleteXxxById` / `existingXxxIds`：按 id 单删与「删前存在性检查」。
 *
 * 因此新增本 DAO，**不改既有 DAO 的任何语义**（既有写路径零影响）。
 * 空列表调用由上层（[com.minibook.data.local.sync.SyncEntityAdapter]）先短路——
 * Room 会把空 `IN ()` 展开成非法 SQL。
 */
@Dao
interface SyncDao {

    // ---------- 待上行集合（updatedAt >= since） ----------

    @Query("SELECT * FROM ledgers WHERE updatedAt >= :since")
    suspend fun ledgersSince(since: String): List<LedgerEntity>

    @Query("SELECT * FROM categories WHERE updatedAt >= :since")
    suspend fun categoriesSince(since: String): List<CategoryEntity>

    @Query("SELECT * FROM transactions WHERE updatedAt >= :since")
    suspend fun transactionsSince(since: String): List<TransactionEntity>

    @Query("SELECT * FROM debts WHERE updatedAt >= :since")
    suspend fun debtsSince(since: String): List<DebtEntity>

    @Query("SELECT * FROM budgets WHERE updatedAt >= :since")
    suspend fun budgetsSince(since: String): List<BudgetEntity>

    @Query("SELECT * FROM user_category_rules WHERE updatedAt >= :since")
    suspend fun userCategoryRulesSince(since: String): List<UserCategoryRuleEntity>

    // ---------- pull 落地（REPLACE 语义） ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedger(entity: LedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(entity: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(entity: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(entity: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserCategoryRule(entity: UserCategoryRuleEntity)

    // ---------- 云端墓碑下发 / 冲突落档后的本地删除 ----------

    @Query("DELETE FROM ledgers WHERE id = :id")
    suspend fun deleteLedgerById(id: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtById(id: String)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: String)

    @Query("DELETE FROM user_category_rules WHERE id = :id")
    suspend fun deleteUserCategoryRuleById(id: String)
}
