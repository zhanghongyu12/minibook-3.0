package com.minibook.domain.model

/**
 * 交易筛选条件，对齐 API §3（TransactionDataSource.observeByLedger 第二参数）。
 *
 * 可空字段 = 不过滤：type=全部(null)/expense/income；categoryId=null 不限分类；
 * dateFrom/dateTo 为 `yyyy-MM-dd` 起止区间（Phase 2 搜索复用同一查询）。
 * 与 DATABASE §6.1 observeFiltered(ledgerId, type?, categoryId?, dateFrom?, dateTo?) 参数化写法一一对应。
 */
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)