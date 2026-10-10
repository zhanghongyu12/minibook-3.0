package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 分类实体，对齐 DATABASE §4.3。
 *
 * isBuiltIn 为 Boolean（INTEGER 0/1），true=内置禁删。
 * 「其他」分类 order 恒为 99 置末尾；自定义分类 order 占 1~98。
 * 无 syncStatus：F-15 同步**不依赖该列**（靠 `updatedAt` 差分 + `sync_state` 基线判定，
 * `docs/14_cloud_sync.md §5.3`），故本轮未给分类加该列。
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["ledgerId", "type"]),
        Index(value = ["ledgerId", "order"]),
    ],
)
@Serializable
data class CategoryEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,
    val isBuiltIn: Boolean,
    val order: Int,
    val createdAt: String,
    val updatedAt: String,
)