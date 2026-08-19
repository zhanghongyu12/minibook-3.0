package com.minibook.domain.repository

import com.minibook.data.datasource.BudgetDataSource
import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.model.Budget
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 预算 Repository 实现（TASK-012 回溯补齐）。
 *
 * 主体委托 [BudgetDataSource]（ARCHITECTURE §4.1 纯转发）；[setTotalBudget] 的
 * 「先删后插」（DEC-008）在 Repository 层事务内完成——经
 * [com.minibook.data.datasource.TransactionRunner.runInTransaction] 包
 * `deleteTotal → upsertTotal` 整体原子（与
 * [CategoryRepositoryImpl.deleteCategoryWithMigration] 同一事务基座）。只依赖
 * `data/datasource` 接口，不感知 Room/SQL/SQLCipher。
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val dataSource: BudgetDataSource,
    private val transactionRunner: TransactionRunner,
) : BudgetRepository {

    override suspend fun setTotalBudget(ledgerId: String, month: YearMonth, amount: Long) {
        // DEC-008：月度总预算（categoryId=null）先删后插保证唯一，DELETE+REPLACE 须整体原子。
        transactionRunner.runInTransaction {
            dataSource.deleteTotal(ledgerId, month)
            dataSource.upsertTotal(ledgerId, month, amount)
        }
    }

    override suspend fun setCategoryBudget(ledgerId: String, month: YearMonth, categoryId: String, amount: Long) =
        dataSource.upsertCategory(ledgerId, month, categoryId, amount)

    override suspend fun clearTotalBudget(ledgerId: String, month: YearMonth) =
        dataSource.deleteTotal(ledgerId, month)

    override suspend fun clearCategoryBudget(ledgerId: String, month: YearMonth, categoryId: String) =
        dataSource.deleteByCategory(ledgerId, month, categoryId)

    override suspend fun deleteByLedger(ledgerId: String) = dataSource.deleteByLedger(ledgerId)

    override fun observeByMonth(ledgerId: String, month: YearMonth): Flow<List<Budget>> =
        dataSource.observeByMonth(ledgerId, month)
}
