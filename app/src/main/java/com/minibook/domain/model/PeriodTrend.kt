package com.minibook.domain.model

/**
 * 报表·收支趋势桶（F-07），对齐 API §12.2 / DATABASE §10.3。
 *
 * - [bucket] 时间桶：月度视图=date `yyyy-MM-dd`、年度视图=`yyyy-MM`（substr(date,1,7)）；
 * - [expenseFen]/[incomeFen] 为该桶支出/收入合计，金额单位「分」（Long，禁浮点）；
 * - [balanceFen] 派生结余 = incomeFen - expenseFen（供结余走势，domain 层整数跑表，
 *   累计结余由 UseCase 层按桶序列累加，DATABASE §10.3 不另写 SQL）。
 */
data class PeriodTrend(
    val bucket: String,
    val expenseFen: Long,
    val incomeFen: Long,
) {
    /** 结余 = 收入 - 支出（分）；负数表示净支出。 */
    val balanceFen: Long get() = incomeFen - expenseFen
}