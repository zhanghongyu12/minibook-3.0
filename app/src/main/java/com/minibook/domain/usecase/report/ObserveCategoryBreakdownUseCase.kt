package com.minibook.domain.usecase.report

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionType
import com.minibook.domain.repository.TransactionRepository
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 报表·分类占比切片（图 2 产物，PRD F-07 / DEC-011）：分类元信息 + 区间合计金额（分）。
 *
 * [category] 为分类元信息（name/icon/color）；分类已删（不在当前分类流内）则 null，
 * UI 以 [categoryId] 兜底展示（DATABASE §10.3：不在 SQL 内 JOIN，分类名/色由 UI 经分类流映射）。
 */
data class CategorySlice(
    val categoryId: String,
    val totalFen: Long,
    val category: Category?,
)

/**
 * 观察当前账本分类占比汇总（TASK-036，PRD F-07 / DEC-011），对齐 UI §8.2 / API §12.2。
 *
 * **两源组合**：`combine(
 *  [TransactionRepository.observeCategoryAgg]([type] + 区间 → 各分类合计)、
 *  [ObserveCategoriesUseCase.observe](同 type 分类流 → 分类元信息 / order 排序) )`
 *  → `List<CategorySlice>`。收入/支出一次一张（UI 以「收入 | 支出」SegmentedButton 切换口径）。
 *
 * @param type 占比口径（收入 / 支出）。
 * @param period 视图目标（月视图 = 目标月份；年视图 = 目标年份）。
 * @param granularity 区间范围：月视图 = 当月、年视图 = 当年（与图 1 同口径），复用 [periodStart]/[periodEnd]。
 *
 * **observe 特殊性（同 ObserveTrendUseCase，TASK-013/015 方案）**：直注 [LedgerDataStore] 以
 * `currentLedgerId` 流做 `flatMapLatest`（切账本重订阅、口径不混账）；当前账本未初始化发空列表保活。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCategoryBreakdownUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerDataStore: LedgerDataStore,
    private val observeCategories: ObserveCategoriesUseCase,
) {

    fun observe(
        type: TransactionType,
        period: YearMonth,
        granularity: GRANULARITY,
    ): Flow<List<CategorySlice>> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                // 首启 Bootstrap 前无当前账本：发空列表保活；Bootstrap 写入后自动重订阅。
                flowOf(emptyList())
            } else {
                combine(
                    transactionRepository.observeCategoryAgg(
                        ledgerId,
                        type,
                        periodStart(period, granularity),
                        periodEnd(period, granularity),
                    ),
                    observeCategories.observe(type.toCategoryType()),
                ) { aggs, categories ->
                    buildSlices(aggs, categories)
                }
            }
        }

    /** aggs × 分类元信息 → 切片（金额 >0 保留，按金额降序；分类已删置底以 id 兜底）。 */
    private fun buildSlices(
        aggs: List<CategoryAgg>,
        categories: List<Category>,
    ): List<CategorySlice> {
        val categoryById = categories.associateBy { it.id }
        return aggs
            .asSequence()
            .filter { it.totalFen > 0L }
            .map { agg ->
                CategorySlice(
                    categoryId = agg.categoryId,
                    totalFen = agg.totalFen,
                    category = categoryById[agg.categoryId],
                )
            }
            .sortedWith(
                compareByDescending<CategorySlice> { it.totalFen }
                    .thenBy { it.category?.order ?: 99 },
            )
            .toList()
    }

    /** [TransactionType] → 分类流过滤用 [CategoryType]（两者取值语义一致）。 */
    private fun TransactionType.toCategoryType(): CategoryType = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
    }
}
