package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 交易实体，对齐 DATABASE §4.1。
 *
 * amount 一律为「分」单位的 Long（无浮点）。
 * date 为 `yyyy-MM-dd` 发生日期；createdAt/updatedAt 为 ISO 8601 完整时间戳。
 * syncStatus 默认 `'local'`，走 StateMachine（Phase 3 云端同步预留）。
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["ledgerId", "date"]),
        Index(value = ["ledgerId", "type"]),
        Index(value = ["ledgerId", "categoryId"]),
        Index(value = ["ledgerId", "syncStatus"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val type: String,
    val amount: Long,
    val categoryId: String,
    val description: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = "local",
)