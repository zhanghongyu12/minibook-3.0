package com.minibook.domain.model

/**
 * 账本领域模型，对齐 DATABASE §4.4 / API §10。
 *
 * isDefault 为 Boolean（默认 false）；至少一个账本为默认，默认账本禁删。
 * 「当前账本」由 LedgerDataStore 提供，不属于本模型字段。
 */
data class Ledger(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean = false,
    val position: Int = 0,
    val createdAt: String,
    val updatedAt: String,
)