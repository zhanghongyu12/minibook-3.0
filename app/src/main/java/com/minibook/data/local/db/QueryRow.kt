package com.minibook.data.local.db

/**
 * DAO 查询 DTO，对齐 DATABASE §9.6（字段名与 SQL 别名对齐）。
 *
 * 仅供 Room DAO 查询结果映射使用，非实体、非 domain 模型：
 * - [SummaryRow]：月结余聚合结果（`monthSummary`）。字段名对齐 SQL 别名
 *   `expenseFen` / `incomeFen`；派生 [balance]（income - expense）供 domain 层
 *   `MonthSummary` 映射（DATABASE §4.1 注：DB 端仅按 type sum 口径）。
 *   `balance` 为只读派生属性（非 Room 列，仅供消费侧取值）。
 * - [NetByPartyRow]：按对象净额汇总结果（`observeNetByParty`）。字段名对齐
 *   SQL 别名 `netFen`（单位分）。正值对方欠我 / 负值我欠对方。
 */
data class SummaryRow(
    val expenseFen: Long,
    val incomeFen: Long,
) {
    /** 结余 = 收入 - 支出（分）。负数表示净支出。 */
    val balance: Long get() = incomeFen - expenseFen
}

data class NetByPartyRow(
    val partyName: String,
    val netFen: Long,
)

/**
 * 报表·收支趋势桶（`observeDailyTrend` / `observeMonthlyTrend`）查询结果 DTO。
 * 字段名对齐 DATABASE §10.3 SQL 别名 `bucket` / `expenseFen` / `incomeFen`；
 * `bucket` 为日期桶（月度=date `yyyy-MM-dd`、年度=substr(date,1,7) `yyyy-MM`）。
 */
data class PeriodTrendRow(
    val bucket: String,
    val expenseFen: Long,
    val incomeFen: Long,
)

/**
 * 报表·分类占比 / 预算「已用」聚合结果 DTO（`observeCategoryAgg`）。
 * 字段名对齐 SQL 别名 `categoryId` / `totalFen`（单位分）。
 */
data class CategoryAggRow(
    val categoryId: String,
    val totalFen: Long,
)