package com.minibook.data.datasource

import com.minibook.domain.model.MonthSummary
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * 交易（收支记录，PRD F-01）数据源契约，逐条对齐 API §3。
 *
 * 纯接口、无实现；本期唯一实现为 RoomDataSourceImpl（TASK-008），经 mapper
 * 由 `TransactionEntity` 转换 domain 模型，接口自身不感知 Room/Entity/SQLCipher。
 *
 * 通用约定（API §1）：
 * - 所有业务查询首参统一 `ledgerId`（ARCHITECTURE §8.1），违反即契约破坏；
 * - 写/读单值用 `suspend`（Room suspend 自动切后台）；列表/汇总观察用 [Flow]；
 * - 金额一律 `Long` 分（DATABASE §9），杜绝浮点。
 */
interface TransactionDataSource {

    /**
     * 新增一笔交易；金额为正整分（≤0 由校验层拦截）。
     *
     * @return 生成的 UUID（离线生成，API §1）。
     */
    suspend fun insert(tx: Transaction): String

    /**
     * 按 [Transaction.id] 更新；`type/amount/categoryId/description/date` 可改，
     * `createdAt` 不可改，`updatedAt` 由实现方自动刷新（API §3 语义）。
     */
    suspend fun update(tx: Transaction)

    /** 物理删除；id 不存在则为空操作。 */
    suspend fun deleteById(id: String)

    /**
     * 按 [ledgerId] 批删该账本全部交易（删账本时调用，DATABASE §4.4）。
     *
     * 与单条 [deleteById] 不同：无 id 维度、整表按账本清空；删账本原子性由
     * TASK-012 DeleteLedgerUseCase 单事务编排承载。
     */
    suspend fun deleteByLedger(ledgerId: String)

    /**
     * 账本内按时间倒序返回交易流；[filter] 为全部/支出/收入 + 分类 + 日期区间
     * （API §3）。冷流，仅订阅时执行。Phase 2 搜索复用同一参数化查询。
     */
    fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>>

    /**
     * 当月收入/支出合计（分）与结余；DB 端 SQL 聚合（DATABASE §6.1 monthSummary）。
     *
     * @param month 目标月份，`java.time.YearMonth`（minSdk 26 起 java.time 可用）。
     */
    suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary

    /** 单条查询，不存在返回 `null`。 */
    suspend fun getById(id: String): Transaction?

    /**
     * 删除前关联检查：统计引用该分类的交易数；>0 则 UI 提示走迁移流程
     * （TASK-015 DeleteCategoryWithMigrationUseCase / UI D-15 依赖）。
     *
     * 与 [CategoryDataSource.countTransactionsByCategory] 同口径；本接口按任务
     * 要求同时暴露该能力（API §3 原文未列此项，见 TASK-007 任务说明）。
     */
    suspend fun countTransactionsByCategory(categoryId: String): Int
}
