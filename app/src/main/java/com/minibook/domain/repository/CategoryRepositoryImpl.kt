package com.minibook.domain.repository

import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionFilter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 分类 Repository 实现（TASK-009）。
 *
 * 主体委托 [CategoryDataSource]（ARCHITECTURE §4.1 纯转发）；迁移编排
 * [deleteCategoryWithMigration] 额外组合 [TransactionDataSource]（Repository 允许
 * 跨 DataSource 聚合，§4.1「组合本地+未来远程」）。只依赖 `data/datasource`
 * 接口，不感知 Room/SQL/SQLCipher。
 *
 * **原子性闭合（TASK-009）**：`deleteCategoryWithMigration` 的「取关联→迁移→删除」
 * 整体包进 [TransactionRunner.runInTransaction]（见
 * [CategoryRepository.deleteCategoryWithMigration] 原子性契约），由数据层事务原语
 * 保证 DB 级原子回滚；事务细节隔离在 `data/local/datasource/RoomTransactionRunner`，
 * 本类仍只依赖 `data/datasource` 接口（合规 DEC-001）。
 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDataSource: CategoryDataSource,
    private val transactionDataSource: TransactionDataSource,
    private val transactionRunner: TransactionRunner,
) : CategoryRepository {

    override suspend fun upsertList(categories: List<Category>) = categoryDataSource.upsertList(categories)

    override suspend fun insert(category: Category): String = categoryDataSource.insert(category)

    override suspend fun update(category: Category) = categoryDataSource.update(category)

    override suspend fun deleteById(id: String) = categoryDataSource.deleteById(id)

    override suspend fun deleteByLedger(ledgerId: String) = categoryDataSource.deleteByLedger(ledgerId)

    override suspend fun countByLedger(ledgerId: String): Long = categoryDataSource.countByLedger(ledgerId)

    override fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>> =
        categoryDataSource.observeByLedger(ledgerId, type)

    override suspend fun countTransactionsByCategory(categoryId: String): Int =
        categoryDataSource.countTransactionsByCategory(categoryId)

    override suspend fun deleteCategoryWithMigration(ledgerId: String, categoryId: String, targetCategoryId: String) {
        // 编排顺序见接口注释；前置业务校验归 UseCase，此处只保证「取关联→迁移→删除」。
        // 整体包进 TransactionRunner.runInTransaction：第 k 条 update 失败 → 全部回滚，无悬空引用。
        transactionRunner.runInTransaction {
            val transactions = transactionDataSource.observeByLedger(
                ledgerId = ledgerId,
                filter = TransactionFilter(categoryId = categoryId),
            ).first()
            transactions.forEach { tx -> transactionDataSource.update(tx.copy(categoryId = targetCategoryId)) }
            categoryDataSource.deleteById(categoryId)
        }
    }
}
