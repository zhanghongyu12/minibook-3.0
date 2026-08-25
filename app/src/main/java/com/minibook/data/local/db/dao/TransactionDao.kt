package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.CategoryAggRow
import com.minibook.data.local.db.PeriodTrendRow
import com.minibook.data.local.db.SummaryRow
import com.minibook.data.local.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 交易 DAO，严格对齐 DATABASE §6.1。
 *
 * - 所有查询第一过滤条件为 `ledgerId`（§5/§6）；金额一律 Long 分。
 * - 列表/汇总返回 [Flow]；CRUD 用 `suspend`。
 * - 与 d[observeFiltered] 同一参数化写法（`AND (x = :x OR :x IS NULL)`）供
 *   Phase 2 搜索复用。
 */
@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(tx: TransactionEntity)

    /**
     * 批量插入（F-12 账单导入，DEC-019 / API §13.5）。
     *
     * `IGNORE` 冲突策略：命中 `UNIQUE(ledgerId, externalId)` 冲突（含文件内重复）时忽略该行。
     * 返回 [List] 与入参同长：`-1` 表示该下标被忽略（冲突），否则为新插入的 rowId——
     * 数据层 `insertBatch` 以此统计「实际插入行数」，UseCase 补计「已存在」（DEC-019）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<TransactionEntity>): List<Long>

    /**
     * 取账本内全部已存在的 externalId（非空列），供导入幂等预查（DEC-019）。
     * 唯一索引 `UNIQUE(ledgerId, externalId)` 的 NULL 互异语义 → 手动记录（NULL）不返回。
     */
    @Query("SELECT externalId FROM transactions WHERE ledgerId=:ledgerId AND externalId IS NOT NULL")
    suspend fun getAllExternalIds(ledgerId: String): List<String>

    @Query(
        """
        UPDATE transactions
        SET type=:type, amount=:amount, categoryId=:categoryId,
            description=:description, date=:date, updatedAt=:updatedAt
        WHERE id=:id AND ledgerId=:ledgerId
        """,
    )
    suspend fun update(
        id: String,
        ledgerId: String,
        type: String,
        amount: Long,
        categoryId: String,
        description: String?,
        date: String,
        updatedAt: String,
    )

    @Query("DELETE FROM transactions WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun deleteById(id: String, ledgerId: String)

    /**
     * 批量物理删除（记账页批量管理模式）：`id IN (:ids)` 一次性删除多笔交易，
     * 返回实际删除行数（Room `DELETE` 返回值 = 受影响行数）。
     *
     * UUID 全局唯一，故不限定 ledgerId（与 [deleteById] 的账本维度守卫不同——批删按已选
     * id 精确命中）；空列表由调用方（DeleteTransactionsUseCase）短路，不触本方法。
     */
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    /**
     * 批量改分类（记账页批量管理）：`id IN (:ids)` 一次更新多笔交易的分类与 updatedAt，
     * 返回实际更新行数（UUID 全局唯一，不限定 ledgerId；空列表由 UseCase 短路）。
     */
    @Query("UPDATE transactions SET categoryId = :categoryId, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateCategoryByIds(ids: List<String>, categoryId: String, updatedAt: String): Int

    /**
     * 批量改类型（支出/收入）：`id IN (:ids)` 一次更新多笔交易的 type 与 updatedAt，
     * 返回实际更新行数。`type` 为存储值（'expense'/'income'，与 [update] 的 String 列同口径）。
     */
    @Query("UPDATE transactions SET type = :type, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateTypeByIds(ids: List<String>, type: String, updatedAt: String): Int

    /** 账本内按日期分组（首页/列表）：date DESC、组内 createdAt DESC。 */
    @Query("SELECT * FROM transactions WHERE ledgerId=:ledgerId ORDER BY date DESC, createdAt DESC")
    fun observeAll(ledgerId: String): Flow<List<TransactionEntity>>

    /**
     * 筛选：全部/支出/收入 +（可选）分类 + 日期区间 +（Phase 2）备注模糊 + 金额区间。
     * 参数化写法 `AND (col=:param OR :param IS NULL)`，null 即不过滤（§6.1 / §10.2）；
     * Phase 2 搜索（DEC-013）复用同一查询：备注映射 description 列 `LIKE '%'||:remark||'%'`，
     * 金额区间 `amountMin/amountMax` 单位「分」、含端点（`min > max` 由上层拒绝）。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ledgerId=:ledgerId
          AND (type = :type OR :type IS NULL)
          AND (categoryId = :categoryId OR :categoryId IS NULL)
          AND (date >= COALESCE(:dateFrom, date))
          AND (date <= COALESCE(:dateTo, date))
          AND (:remark IS NULL OR description LIKE '%' || :remark || '%')
          AND (:amountMin IS NULL OR amount >= :amountMin)
          AND (:amountMax IS NULL OR amount <= :amountMax)
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeFiltered(
        ledgerId: String,
        type: String?,
        categoryId: String?,
        dateFrom: String?,
        dateTo: String?,
        remark: String?,
        amountMin: Long?,
        amountMax: Long?,
    ): Flow<List<TransactionEntity>>

    /**
     * 月结余：命中 `(ledgerId, date)` 复合索引的 BETWEEN 范围查询。
     * 结果映射 [SummaryRow]，balance 由 domain 层聚合（income - expense）。
     */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END), 0) AS expenseFen,
          COALESCE(SUM(CASE WHEN type='income'  THEN amount ELSE 0 END), 0) AS incomeFen
        FROM transactions
        WHERE ledgerId=:ledgerId AND date BETWEEN :monthStart AND :monthEnd
        """,
    )
    suspend fun monthSummary(ledgerId: String, monthStart: String, monthEnd: String): SummaryRow

    /**
     * 报表·月度收支趋势：按天桶（bucket=date），命中 `(ledgerId, date)` 区间索引。
     * 对齐 DATABASE §10.3；`start/end` 为当月首/末日（`yyyy-MM-dd`）。
     * `expenseFen`/`incomeFen` 均 COALESCE 兜 0（无数据桶不出现，由 GROUP BY 决定）。
     */
    @Query(
        """
        SELECT date AS bucket,
               COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END), 0) AS expenseFen,
               COALESCE(SUM(CASE WHEN type='income'  THEN amount ELSE 0 END), 0) AS incomeFen
        FROM transactions
        WHERE ledgerId=:ledgerId AND date BETWEEN :start AND :end
        GROUP BY date
        ORDER BY bucket ASC
        """,
    )
    fun observeDailyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrendRow>>

    /**
     * 报表·年度收支趋势：按月桶（substr(date,1,7)，与 month 'yyyy-MM' 口径一致，§9.5）。
     * 命中 `(ledgerId, date)` 区间索引；`start/end` 为当年首/末日。
     */
    @Query(
        """
        SELECT substr(date,1,7) AS bucket,
               COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END), 0) AS expenseFen,
               COALESCE(SUM(CASE WHEN type='income'  THEN amount ELSE 0 END), 0) AS incomeFen
        FROM transactions
        WHERE ledgerId=:ledgerId AND date BETWEEN :start AND :end
        GROUP BY substr(date,1,7)
        ORDER BY bucket ASC
        """,
    )
    fun observeMonthlyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrendRow>>

    /**
     * 报表·分类占比 / 预算「已用」：按 type + categoryId 分组金额合计（`totalFen` Long 分）。
     * 命中 `(ledgerId, date)` 范围扫描；结果分组走 `(ledgerId, categoryId)`（DATABASE §10.3/§10.5）。
     * [type] 为存储值（'expense'/'income'），收入/支出分开聚合，不在 SQL 内 JOIN 分类表。
     */
    @Query(
        """
        SELECT categoryId,
               COALESCE(SUM(amount), 0) AS totalFen
        FROM transactions
        WHERE ledgerId=:ledgerId AND type=:type AND date BETWEEN :start AND :end
        GROUP BY categoryId
        ORDER BY totalFen DESC
        """,
    )
    fun observeCategoryAgg(ledgerId: String, type: String, start: String, end: String): Flow<List<CategoryAggRow>>

    /** 分类删除前关联检查（命中 `(ledgerId, categoryId)` 索引）。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:categoryId AND ledgerId=:ledgerId")
    suspend fun countByCategory(categoryId: String, ledgerId: String): Int
}