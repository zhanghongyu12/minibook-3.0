package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minibook.data.local.db.NetByPartyRow
import com.minibook.data.local.db.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

/**
 * 债务 DAO，严格对齐 DATABASE §6.2。
 *
 * - 借/还平级模型：kind × direction 四态（§4.2）。
 * - 净额口径（PRD F-02，正=对方欠我，负=我欠对方）：
 *   | kind   | direction | 对净额贡献 |
 *   |--------|-----------|-----------|
 *   | lend   | owe_me    | +amount（借出）  |
 *   | lend   | i_owe     | -amount（借入）  |
 *   | repay  | owe_me    | -amount（收到还款）|
 *   | repay  | i_owe     | +amount（我还款）  |
 *   未知组合贡献 0（四态守卫）。由 [observeNetByParty] 的 CASE WHEN 一次算。
 */
@Dao
interface DebtDao {

    @Insert
    suspend fun insert(debt: DebtEntity)

    @Query(
        """
        UPDATE debts
        SET partyName=:partyName, amount=:amount, kind=:kind, direction=:direction,
            date=:date, remark=:remark, updatedAt=:updatedAt
        WHERE id=:id AND ledgerId=:ledgerId
        """,
    )
    suspend fun update(
        id: String,
        ledgerId: String,
        partyName: String,
        amount: Long,
        kind: String,
        direction: String,
        date: String,
        remark: String?,
        updatedAt: String,
    )

    @Query("DELETE FROM debts WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun deleteById(id: String, ledgerId: String)

    /** 明细视图：账本内按时间排序。 */
    @Query("SELECT * FROM debts WHERE ledgerId=:ledgerId ORDER BY date DESC, createdAt DESC")
    fun observeAll(ledgerId: String): Flow<List<DebtEntity>>

    /**
     * 按对象视图：按 partyName 分组净额汇总。
     * 四态 CASE WHEN 贡献一次算（§6.2 Q-D2），命中 `(ledgerId, partyName)` 索引。
     */
    @Query(
        """
        SELECT partyName,
               COALESCE(SUM(CASE
                 WHEN (kind='lend'  AND direction='owe_me') THEN  amount
                 WHEN (kind='lend'  AND direction='i_owe')  THEN -amount
                 WHEN (kind='repay' AND direction='owe_me') THEN -amount
                 WHEN (kind='repay' AND direction='i_owe')  THEN  amount
                 ELSE 0 END), 0) AS netFen
        FROM debts
        WHERE ledgerId=:ledgerId
        GROUP BY partyName
        ORDER BY netFen DESC
        """,
    )
    fun observeNetByParty(ledgerId: String): Flow<List<NetByPartyRow>>

    /** 筛选：kind（lend/repay）与 direction（owe_me/i_owe）可选；null 即不过滤（A-2 契约）。 */
    @Query(
        """
        SELECT * FROM debts
        WHERE ledgerId=:ledgerId
          AND (:kind IS NULL OR kind=:kind)
          AND (:direction IS NULL OR direction=:direction)
        ORDER BY date DESC
        """,
    )
    fun observeFiltered(
        ledgerId: String,
        kind: String?,
        direction: String?,
    ): Flow<List<DebtEntity>>
}