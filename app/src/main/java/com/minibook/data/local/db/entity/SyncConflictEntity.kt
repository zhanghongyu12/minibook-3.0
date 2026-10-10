package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 冲突留档（Room v4 新表，F-15 / DEC-033，`docs/14_cloud_sync.md §4.5 / §5.3`）。
 *
 * LWW 判据靠 `updatedAt`，而客户端时钟可能偏移 ⇒ **绝不允许静默丢弃输家**：
 * 每次冲突都把本地与云端两份 JSON 原样留档，UI 显示「冲突 N 条」，后续可人工纠正
 * （本轮只留档 + 计数，不做逐条选择 UI）。
 *
 * `resolvedAt` / `resolution` 为二期「人工选择保留哪份」预留，本轮恒 NULL。
 */
@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["ledgerId"]),
    ],
)
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val ledgerId: String,
    /** 冲突发生时本地那份的 JSON（输家或赢家都原样保留）。 */
    val localJson: String,
    /** 冲突发生时云端那份的 JSON。 */
    val remoteJson: String,
    val detectedAt: String,
    val resolvedAt: String? = null,
    val resolution: String? = null,
)
