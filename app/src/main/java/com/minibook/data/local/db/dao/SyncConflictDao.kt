package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.entity.SyncConflictEntity

/**
 * 冲突留档 DAO（F-15 / DEC-033，`docs/14_cloud_sync.md §4.5`）。
 *
 * 计数只算未处理（`resolvedAt IS NULL`）——UI 状态行的「冲突 N 条」口径。
 */
@Dao
interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: SyncConflictEntity)

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE resolvedAt IS NULL")
    suspend fun unresolvedCount(): Int

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id")
    suspend fun find(id: String): SyncConflictEntity?

    /** 登出/清账号态时一并清空（留档属于「该账号的同步历史」）。 */
    @Query("DELETE FROM sync_conflicts")
    suspend fun clear()
}
