package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minibook.data.local.db.entity.SyncChangeEntity

/**
 * 同步变更 DAO（Phase 3 云同步预留，本期只建表不写数据），对齐 DATABASE §6.5。
 *
 * [pendingChanges] 增量时间线（按 timestamp ASC）；[markSynced] 消费后清理。
 * 本期无调用方（TASK-008 mapper 接通后由 SyncChangeDataSource 预留扩展点调用）。
 */
@Dao
interface SyncChangeDao {

    @Insert
    suspend fun insert(change: SyncChangeEntity)

    /** 取未同步变更，按变更时间升序（Phase 3 push 出口）。 */
    @Query("SELECT * FROM sync_changes WHERE ledgerId=:ledgerId ORDER BY timestamp ASC")
    suspend fun pendingChanges(ledgerId: String): List<SyncChangeEntity>

    /** 标记已同步：整批按 id 删除变更记录。 */
    @Query("DELETE FROM sync_changes WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}