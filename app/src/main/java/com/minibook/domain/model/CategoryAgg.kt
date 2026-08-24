package com.minibook.domain.model

/**
 * 报表·分类占比 / 预算「已用」聚合结果（F-07 / F-08），对齐 API §12.2 / DATABASE §10.3。
 *
 * - [categoryId] 分类 id（UUID）；分类名/图标/色由 UI 经既有分类流做 id→元信息映射，
 *   不在 SQL 内 JOIN（DATABASE §10.3）；分类已删则 UI 以 id 兜底展示。
 * - [totalFen] 该分类在区间内合计金额，单位「分」（Long，禁浮点）。
 */
data class CategoryAgg(
    val categoryId: String,
    val totalFen: Long,
)