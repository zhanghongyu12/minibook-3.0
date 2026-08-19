package com.minibook.data.datasource

import com.minibook.domain.model.Budget
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * 预算（PRD F-08）数据源契约，逐条对齐 API §7。
 *
 * **Phase 2 预留**：接口现在落盘保证未来零迁移接入，本期不实现不接线。
 * 表结构已随 version 1 建表（DATABASE §4.5），DAO 骨架已落盘（DATABASE §6.5）。
 *
 * D-7 业务唯一语义：月度总预算（`categoryId=null`）在 **Repository 层事务内
 * 「先删后插」** 保证唯一（SQLite 中 `(ledgerId, month, NULL)` 的 NULL 彼此视为
 * 不同，DB 不加触发器）；分类预算由 DB `UNIQUE(ledgerId, month, categoryId)`
 * 约束直接保证（API §7 / DEC-008）。
 */
interface BudgetDataSource {

    /** 设置月度总预算（`categoryId=null`）。Repository 层先删后插保证唯一（D-7）。 */
    suspend fun upsertTotal(ledgerId: String, month: YearMonth, amount: Long)

    /** 设置分类预算；由 DB UNIQUE 约束保证唯一。 */
    suspend fun upsertCategory(ledgerId: String, month: YearMonth, categoryId: String, amount: Long)

    /** 删除月度总预算（配合先删后插，D-7）。 */
    suspend fun deleteTotal(ledgerId: String, month: YearMonth)

    /** 删除分类预算。 */
    suspend fun deleteByCategory(ledgerId: String, month: YearMonth, categoryId: String)

    /**
     * 按 [ledgerId] 批删该账本全部预算（删账本时调用，DATABASE §4.4）。
     *
     * 与 [deleteTotal]/[deleteByCategory] 的月度维度不同：无 month 入参、整表按账本
     * 清空（含总预算与分类预算）；删账本原子性由 TASK-012 DeleteLedgerUseCase
     * 单事务编排承载。
     */
    suspend fun deleteByLedger(ledgerId: String)

    /** 账本+月份取月度预算列表（含总预算与分类预算，categoryId 区分）。 */
    fun observeByMonth(ledgerId: String, month: YearMonth): Flow<List<Budget>>
}
