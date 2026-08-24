package com.minibook.domain.model

/**
 * 债务筛选条件，对齐 API §4 / §12.1（DebtDataSource.observeByLedger 第二参数，A-2 确认展开）。
 *
 * 展开 kind / direction / partyName 三个可空字段：可空 = 不过滤。
 * 与 DATABASE §10.2 observeFiltered(ledgerId, kind?, direction?, partyName?) 逐字段对齐。
 * [partyName] 姓名模糊（`LIKE '%partyName%'`）属 Phase 2 对同一 DebtFilter 扩展（PRD F-06 / DEC-013）。
 */
data class DebtFilter(
    val kind: DebtKind? = null,
    val direction: DebtDirection? = null,
    val partyName: String? = null,
)