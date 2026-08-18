package com.minibook.domain.model

/**
 * 月度汇总领域模型，对齐 API §10 / DATABASE §9（金额「分」口径）。
 * balanceFen = incomeFen - expenseFen（DB 端 SQL 聚合，见 DATABASE §6.1 monthSummary）。
 */
data class MonthSummary(
    val incomeFen: Long,
    val expenseFen: Long,
    val balanceFen: Long,
)