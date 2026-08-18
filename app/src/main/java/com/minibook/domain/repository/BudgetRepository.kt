package com.minibook.domain.repository

import com.minibook.domain.model.Budget
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * 预算（PRD F-08）领域聚合入口。
 *
 * **Phase 2 预留接口：只定义契约，本期不实现不接线**（对应 `data/datasource` 的
 * `BudgetDataSource` 同期预留，ARCHITECTURE §3.2 / TASK-009）。未来 Impl 将注入
 * `BudgetDataSource` 并逐方法委托（ARCHITECTURE §4.1），本接口方法签名即未来
 * Impl 的实现契约；表结构已随 version 1 建表（DATABASE §4.5），Phase 2 零迁移接入。
 *
 * **D-7 / DEC-008 业务唯一语义**：月度总预算（`categoryId=null`）在 SQLite 中
 * `(ledgerId, month, NULL)` 的 NULL 彼此视为不同，`REPLACE` 不去重，须在
 * Repository 层「先删后插」保证唯一（见 [setTotalBudget] 时序要求）；
 * 分类预算由 DB `UNIQUE(ledgerId, month, categoryId)` 约束直接保证。
 */
interface BudgetRepository {

    /**
     * 设置月度总预算（`categoryId=null`）。
     *
     * **先删后插（D-7 / DEC-008）**：实现必须先调
     * `BudgetDataSource.deleteTotal(ledgerId, month)` 再调
     * `upsertTotal(ledgerId, month, amount)` —— 否则 REPLACE 无法去重 NULL 行，
     * 同月总预算会累积多行。
     *
     * **原子性注（TASK-009 事务边界已落基座）**：DEC-008 要求此「先删后插」在
     * Repository 层事务内完成；Phase 2 实现时经
     * [com.minibook.data.datasource.TransactionRunner.runInTransaction] 包
     * `deleteTotal → upsertTotal` 整体事务闭合（与
     * [CategoryRepository.deleteCategoryWithMigration] 同一事务基座）。
     */
    suspend fun setTotalBudget(ledgerId: String, month: YearMonth, amount: Long)

    /**
     * 设置分类预算；唯一性由 DB `UNIQUE(ledgerId, month, categoryId)` 约束直接保证
     * （REPLACE 冲突策略）。
     */
    suspend fun setCategoryBudget(ledgerId: String, month: YearMonth, categoryId: String, amount: Long)

    /** 删除月度总预算（配合「先删后插」，D-7）。 */
    suspend fun clearTotalBudget(ledgerId: String, month: YearMonth)

    /** 删除分类预算。 */
    suspend fun clearCategoryBudget(ledgerId: String, month: YearMonth, categoryId: String)

    /** 账本+月份取月度预算列表（含总预算与分类预算，categoryId 区分）。 */
    fun observeByMonth(ledgerId: String, month: YearMonth): Flow<List<Budget>>
}
