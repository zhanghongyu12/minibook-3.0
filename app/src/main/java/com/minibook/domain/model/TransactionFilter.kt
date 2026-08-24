package com.minibook.domain.model

/**
 * 交易筛选条件，对齐 API §3 / §12.1（TransactionDataSource.observeByLedger 第二参数）。
 *
 * 可空字段 = 不过滤：type=全部(null)/expense/income；categoryId=null 不限分类；
 * dateFrom/dateTo 为 `yyyy-MM-dd` 起止区间。Phase 2（DEC-013）扩展：
 * - [remark] 备注模糊（`LIKE '%remark%'`，映射 transactions.description 列）；
 * - [amountMin]/[amountMax] 金额区间（单位「分」、含端点）；min > max 由
 *   ViewModel/UseCase 层拒绝或视为无效（PRD F-06 异常处理）。
 * 与 DATABASE §10.2 observeFiltered(ledgerId, type?, categoryId?, dateFrom?, dateTo?,
 * remark?, amountMin?, amountMax?) 参数化写法一一对应。
 */
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val remark: String? = null,
    val amountMin: Long? = null,
    val amountMax: Long? = null,
)