package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.entity.SyncStateEntity

/**
 * 同步基线 DAO（F-15 / DEC-033，`docs/14_cloud_sync.md §4.2`）。
 *
 * 只经同步引擎读写，不进业务写路径；`delete`/`clear` 只在「墓碑上行成功」或
 * 「登出清空账号态」时调用。
 */
@Dao
interface SyncStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<SyncStateEntity>)

    /** 某类型全部基线（删除差分用：逐类型与本地现存 id 求差）。 */
    @Query("SELECT * FROM sync_state WHERE entityType = :entityType")
    suspend fun byType(entityType: String): List<SyncStateEntity>

    @Query("SELECT * FROM sync_state WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun find(entityType: String, entityId: String): SyncStateEntity?

    @Query("SELECT COUNT(*) FROM sync_state")
    suspend fun count(): Int

    /** 墓碑上行成功后清除基线（两端就此都认为该行不存在）。 */
    @Query("DELETE FROM sync_state WHERE entityType = :entityType AND entityId IN (:ids)")
    suspend fun delete(entityType: String, ids: List<String>)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
