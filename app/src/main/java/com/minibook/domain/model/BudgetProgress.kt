package com.minibook.domain.model

/**
 * 预算进度领域模型（PRD F-08，TASK-037），对齐 DEC-008 / DEC-012。
 *
 * 基于「总额」+「分类」预算与当月实际支出聚合得出，供 P-14 预算页（TASK-038）与
 * 首页超支入口提示消费。**口径 = PRD F-08**：
 * - 已用 = 当月支出累计（分）；
 * - 剩余 = 预算 − 已用（分）；
 * - 超支 = 已用 > 预算（分）。
 *
 * 金额一律 `Long` 分（DATABASE §9 / DEC-001 杜绝浮点），派生值由[剩余/超支]直接计算。
 * [categoryProgress] 携带分类元信息（[Category]），UI 无需再经分类流做 id→元信息映射。
 *
 * @property month 目标月份 `yyyy-MM`（对齐 [Budget.month] / TIME §9.5 口径）。
 * @property totalBudgetFen 月度总预算（分）；未设置时 = 0。
 * @property totalUsedFen 总额已用（分）= 各分类 `CategoryAgg.totalFen` 之和
 *  （等价 `MonthSummary.expenseFen`，DATABASE §10.4 / UI D-27）。
 * @property categoryProgress 分类预算进度（仅已设分类预算且分类仍存在的行）。
 */
data class BudgetProgress(
    val month: String,
    val totalBudgetFen: Long,
    val totalUsedFen: Long,
    val categoryProgress: List<CategoryBudgetProgress>,
) {
    /** 总额剩余（分）= 总额预算 − 总额已用；可为负（超支，PRD F-08）。 */
    val remainingFen: Long get() = totalBudgetFen - totalUsedFen

    /** 总额是否超支：已设预算（>0）且已用 > 预算（PRD F-08：超支 = 已用 > 预算）。 */
    val overBudget: Boolean get() = totalBudgetFen > 0 && totalUsedFen > totalBudgetFen

    /** 是否任一超支：总额超支或任一分类预算超支（DEC-012 首页/入口提示触发条件）。 */
    val hasOverBudget: Boolean get() = overBudget || categoryProgress.any { it.overBudget }
}

/**
 * 单个分类的预算进度（PRD F-08）。
 *
 * @property category 分类元信息（名/图标/色；`observeByLedger` 已按 order 升序）。
 * @property budgetFen 该分类当月预算（分）。
 * @property usedFen 该分类当月实际支出累计（分）= `CategoryAgg.totalFen`。
 */
data class CategoryBudgetProgress(
    val category: Category,
    val budgetFen: Long,
    val usedFen: Long,
) {
    /** 分类剩余（分）= 分类预算 − 分类已用；可为负（超支）。 */
    val remainingFen: Long get() = budgetFen - usedFen

    /** 分类是否超支：已用 > 预算（PRD F-08）。 */
    val overBudget: Boolean get() = usedFen > budgetFen
}
