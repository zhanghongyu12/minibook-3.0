package com.minibook.domain.model

/**
 * 债务筛选条件，对齐 API §4（DebtDataSource.observeByLedger 第二参数，A-2 确认展开）。
 *
 * 仅展开 kind / direction 两个可空字段（MVP）：可空 = 不过滤。
 * 与 DATABASE §6.2 observeFiltered(ledgerId, kind?, direction?) 逐字段对齐。
 * partyName 模糊筛不入 MVP（按对象净额已由 netByParty 聚合支撑），属 Phase 2 对同一
 * DebtFilter 扩展（PRD F-06）。
 */
data class DebtFilter(
    val kind: DebtKind? = null,
    val direction: DebtDirection? = null,
)