package com.minibook.domain.usecase.budget

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Budget
import com.minibook.domain.model.BudgetProgress
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryBudgetProgress
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionType
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.TransactionRepository
import com.minibook.util.TimeFormatter
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 观察当前账本指定月份的预算进度（TASK-037，PRD F-08 / DEC-008 / DEC-012）。
 *
 * **三源组合（对齐 UI D-26 / D-27）**：`combine(
 *  [BudgetRepository.observeByMonth](当月预算：总额 + 分类)、
 *  [TransactionRepository.observeCategoryAgg](type=expense，当月首/末日区间 → 各分类已用)、
 *  [CategoryRepository.observeByLedger](type=expense → 分类元信息 / order 排序) )`
 *  → `[BudgetProgress]`。
 *
 * 口径 = PRD F-08：
 * - 总额已用 = 各分类 `CategoryAgg.totalFen` 之和（等价 `MonthSummary.expenseFen`，
 *   DATABASE §10.4 / UI D-27）；
 * - 剩余 = 预算 − 已用；超支 = 已用 > 预算；[BudgetProgress.hasOverBudget] =
 *   总额或任一分类超支（DEC-012「首页/入口轻量提示」触发条件，展示在 TASK-038）。
 *
 * **observe 特殊性（同 ObserveTransactions/ObserveCategories，TASK-013/015 方案）**：
 * observe 路径须响应账本切换，故直注 [LedgerDataStore]，以 `currentLedgerId` 流做
 * `flatMapLatest`——切账本写后立即发射新 id，取消旧订阅、重跑三源重订阅（§8.3 不混账）。
 * 取值路径（Set/Clear Budget UseCase）仍一律经
 * `com.minibook.domain.common.LedgerProvider`（§8.5）。当前账本未初始化（空串，
 * Bootstrap 前）时发射零值进度而非抛错，Bootstrap 写入后自动重订阅恢复。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBudgetProgressUseCase @Inject constructor(
    private val ledgerDataStore: LedgerDataStore,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * 当前账本 [month] 的预算进度流；任一源（预算写 / 交易增删改 / 分类增改或删 / 账本切换）
     * 变化即自动重算发射。
     *
     * @param month 目标月份（`java.time.YearMonth`）。
     */
    fun observe(month: YearMonth): Flow<BudgetProgress> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            val monthStr = TimeFormatter.formatYearMonth(month)
            if (ledgerId.isBlank()) {
                // 首启 Bootstrap 前无当前账本：发零值进度保活；Bootstrap 写入后自动重订阅。
                flowOf(zeroProgress(monthStr))
            } else {
                combine(
                    budgetRepository.observeByMonth(ledgerId, month),
                    transactionRepository.observeCategoryAgg(
                        ledgerId,
                        TransactionType.EXPENSE,
                        monthStart(month),
                        monthEnd(month),
                    ),
                    categoryRepository.observeByLedger(ledgerId, CategoryType.EXPENSE),
                ) { budgets, aggs, categories ->
                    buildProgress(monthStr, budgets, aggs, categories)
                }
            }
        }

    /** 当月已用 = 各分类 `totalFen` 之和（等价 monthSummary.expenseFen，UI D-27）。 */
    private fun buildProgress(
        monthStr: String,
        budgets: List<Budget>,
        aggs: List<CategoryAgg>,
        categories: List<Category>,
    ): BudgetProgress {
        val aggByCategory = aggs.associateBy { it.categoryId }
        val categoryById = categories.associateBy { it.id }
        val totalBudgetFen = budgets.firstOrNull { it.categoryId == null }?.amount ?: 0L
        val totalUsedFen = aggs.sumOf { it.totalFen }

        val categoryProgress = budgets
            .filter { it.categoryId != null }
            .mapNotNull { budget ->
                val categoryId = budget.categoryId ?: return@mapNotNull null
                // 分类已被删除（不在当前月分类流内）→ 视为无意义孤儿预算，跳过展示。
                val category = categoryById[categoryId] ?: return@mapNotNull null
                CategoryBudgetProgress(
                    category = category,
                    budgetFen = budget.amount,
                    usedFen = aggByCategory[categoryId]?.totalFen ?: 0L,
                )
            }
            .sortedBy { it.category.order }

        return BudgetProgress(
            month = monthStr,
            totalBudgetFen = totalBudgetFen,
            totalUsedFen = totalUsedFen,
            categoryProgress = categoryProgress,
        )
    }

    private fun monthStart(month: YearMonth): String = month.atDay(1).toString()

    private fun monthEnd(month: YearMonth): String = month.atEndOfMonth().toString()

    /** 当前账本未初始化（首启）时的零值进度，保证冷流 collector 不崩。 */
    private fun zeroProgress(monthStr: String): BudgetProgress =
        BudgetProgress(
            month = monthStr,
            totalBudgetFen = 0L,
            totalUsedFen = 0L,
            categoryProgress = emptyList(),
        )
}
