package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 同步变更实体，对齐 DATABASE §4.6（Phase 3 云端出口预留，本期只建表不写数据）。
 *
 * entityType：transaction/debt/category；action：create/update/delete。
 * timestamp 为 ISO 8601 完整时间戳。data 为变更载荷 JSON，delete 可为空。
 */
@Entity(
    tableName = "sync_changes",
    indices = [
        Index(value = ["ledgerId", "entityType"]),
        Index(value = ["ledgerId", "timestamp"]),
        Index(value = ["entityId"]),
    ],
)
data class SyncChangeEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val timestamp: String,
    val data: String?,
)