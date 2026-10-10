package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 账本实体，对齐 DATABASE §4.4。
 *
 * isDefault 为 Boolean（INTEGER 0/1），默认 false；至少一个账本为默认。
 * 默认账本禁删；删除非默认账本 = 事务内按 ledgerId 清 5 表后删本行。
 */
@Entity(
    tableName = "ledgers",
    indices = [Index(value = ["position"]), Index(value = ["isDefault"])],
)
@Serializable
data class LedgerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean = false,
    val position: Int = 0,
    val createdAt: String,
    val updatedAt: String,
)