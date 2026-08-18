package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预算实体，对齐 DATABASE §4.5 / DEC-008。
 *
 * month 为 `yyyy-MM`。categoryId 为 null 表示月度总预算。
 * (ledgerId, month, categoryId) 为 UNIQUE（分类预算天然唯一）；
 * 月度总预算（categoryId=NULL）在 SQL 中 NULL 互不相等，业务层以「先删后插」保证唯一（DEC-008）。
 */
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["ledgerId", "month"]),
        Index(value = ["ledgerId", "month", "categoryId"], unique = true),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val month: String,
    val categoryId: String?,
    val amount: Long,
    val createdAt: String,
    val updatedAt: String,
)