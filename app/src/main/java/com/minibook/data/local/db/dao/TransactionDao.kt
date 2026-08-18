package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    /** 账本内按日期分组（首页/列表）：date DESC、组内 createdAt DESC。 */
    @Query("SELECT * FROM transactions WHERE ledgerId=:ledgerId ORDER BY date DESC, createdAt DESC")
    fun observeAll(ledgerId: String): Flow<List<TransactionEntity>>

    /**
     * 筛选：全部/支出/收入 +（可选）分类 + 日期区间。
     * 参数化写法 `AND (col=:param OR :param IS NULL)`，null 即不过滤（§6.1）；
     * Phase 2 搜索复用同一查询。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ledgerId=:ledgerId
          AND (type = :type OR :type IS NULL)
          AND (categoryId = :categoryId OR :categoryId IS NULL)
          AND (date >= COALESCE(:dateFrom, date))
          AND (date <= COALESCE(:dateTo, date))
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeFiltered(
        ledgerId: String,
        type: String?,
        categoryId: String?,
        dateFrom: String?,
        dateTo: String?,
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

    /** 分类删除前关联检查（命中 `(ledgerId, categoryId)` 索引）。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:categoryId AND ledgerId=:ledgerId")
    suspend fun countByCategory(categoryId: String, ledgerId: String): Int
}