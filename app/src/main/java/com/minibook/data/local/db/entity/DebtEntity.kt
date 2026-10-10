package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 债务实体，对齐 DATABASE §4.2。
 *
 * kind × direction 四态平级模型：lend/repay × owe_me/i_owe。
 * amount 为「分」单位 Long。date 为 `yyyy-MM-dd`。
 * syncStatus 默认 `'local'`。
 */
@Entity(
    tableName = "debts",
    indices = [
        Index(value = ["ledgerId", "date"]),
        Index(value = ["ledgerId", "partyName"]),
        Index(value = ["ledgerId", "kind"]),
        Index(value = ["ledgerId", "direction"]),
        Index(value = ["ledgerId", "syncStatus"]),
    ],
)
@Serializable
data class DebtEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val partyName: String,
    val amount: Long,
    val kind: String,
    val direction: String,
    val date: String,
    val remark: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = "local",
)