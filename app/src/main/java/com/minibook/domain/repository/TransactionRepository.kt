package com.minibook.domain.repository

import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.MonthSummary
import com.minibook.domain.model.PeriodTrend
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * 交易（收支记录，PRD F-01）领域聚合入口，对齐 ARCHITECTURE §4.1。
 *
 * UseCase 层唯一交易入口；实现 [TransactionRepositoryImpl] 委托
 * `data/datasource` 的 `TransactionDataSource`，自身不感知 Room/SQL/SQLCipher
 * （Repository 只依赖 DataSource 接口，ARCHITECTURE §4.1 / DEC-001）。
 *
 * 通用约定（与 DataSource 契约一致）：
 * - 所有业务查询首参统一 `ledgerId`（ARCHITECTURE §8.1）；
 * - 写/读单值用 `suspend`；列表/汇总观察用 [Flow]；
 * - 金额一律 `Long` 分（DATABASE §9），杜绝浮点。
 */
interface TransactionRepository {

    /**
     * 记一笔：新增交易；金额为正整分（≤0 由校验层拦截，TASK-010 Validators）。
     *
     * @return 生成的 UUID（离线生成，API §1）。
     */
    suspend fun insert(tx: Transaction): String

    /**
     * 编辑交易：按 [Transaction.id] 更新，`type/amount/categoryId/description/date` 可改，
     * `createdAt` 不可改，`updatedAt` 由实现方自动刷新（API §3 语义）。
     */
    suspend fun update(tx: Transaction)

    /** 物理删除；id 不存在则为空操作。 */
    suspend fun deleteById(id: String)

    /**
     * 按 [ledgerId] 批删该账本全部交易（删账本时调用，TASK-012 DeleteLedgerUseCase
     * 单事务编排；DATABASE §4.4）。
     */
    suspend fun deleteByLedger(ledgerId: String)

    /**
     * 账本内按时间倒序返回交易流；[filter] 为全部/支出/收入 + 分类 + 日期区间
     * （API §3）。冷流，仅订阅时执行。Phase 2 搜索复用同一参数化查询。
     */
    fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>>

    /**
     * 报表·月度收支趋势（F-07）：按天桶（bucket=date），金额 Long 分（API §12.2 / DATABASE §10.3）。
     */
    fun observeDailyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>>

    /**
     * 报表·年度收支趋势（F-07）：按月桶（bucket=yyyy-MM，substr(date,1,7)），金额 Long 分。
     */
    fun observeMonthlyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>>

    /**
     * 报表·分类占比 / 预算「已用」聚合（F-07 / F-08）：按 [type] + categoryId 分组合计
     * （`totalFen` Long 分，API §12.2 / DATABASE §10.3）。
     */
    fun observeCategoryAgg(ledgerId: String, type: TransactionType, start: String, end: String): Flow<List<CategoryAgg>>

    /**
     * 当月收入/支出合计（分）与结余；DB 端 SQL 聚合（DATABASE §6.1 monthSummary）。
     *
     * @param month 目标月份，`java.time.YearMonth`（minSdk 26 起 java.time 可用）。
     */
    suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary

    /** 单条查询，不存在返回 `null`（编辑表单回填等预留使用方）。 */
    suspend fun getById(id: String): Transaction?

    /**
     * 删除前关联检查：统计引用该分类的交易数；>0 则 UI 提示走迁移流程
     * （TASK-015 DeleteCategoryWithMigrationUseCase / UI D-15 依赖）。
     *
     * 与 [CategoryRepository.countTransactionsByCategory] 同口径（API §3 注）；
     * 分类删除语义归 CategoryRepository，本方法为交易侧计数透出。
     */
    suspend fun countTransactionsByCategory(categoryId: String): Int
}
