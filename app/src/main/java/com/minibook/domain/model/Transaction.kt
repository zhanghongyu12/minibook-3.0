package com.minibook.domain.model

/**
 * 交易领域模型，对齐 DATABASE §4.1 / API §10。
 *
 * amount 为「分」单位 Long（无浮点）；date 为 `yyyy-MM-dd` 发生日期；
 * createdAt/updatedAt 为 ISO 8601 完整时间戳。type 为 `expense`/`income` 枚举。
 */
data class Transaction(
    val id: String,
    val ledgerId: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: String,
    val description: String? = null,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
)