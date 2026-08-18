package com.minibook.domain.model

/**
 * 预算领域模型，对齐 DATABASE §4.5 / API §10（Phase 2 建表，本期接口预留）。
 *
 * month 为 `yyyy-MM`；categoryId 为 null 表示月度总预算，非 null 为分类预算。
 * amount 为「分」单位 Long，≥0。
 */
data class Budget(
    val id: String,
    val ledgerId: String,
    val month: String,
    val categoryId: String? = null,
    val amount: Long,
    val createdAt: String,
    val updatedAt: String,
)