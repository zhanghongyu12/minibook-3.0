package com.minibook.domain.usecase.report

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.PeriodTrend
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.fakeLedgerDataStore
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报表 UseCase 单测（TASK-036 / TASK-034，PRD F-07 / DEC-011 / DATABASE §10.3）。
 *
 * 覆盖：
 * - [ObserveTrendUseCase]：按月/年粒度委托正确（MONTHLY→observeDailyTrend、
 *   YEARLY→observeMonthlyTrend，区间首/末日正确），返回 [ReportTrend] 三序列；
 * - [ObserveBalanceTrendUseCase]：`runningFold` 累计结余整数跑表正确（多桶累加）；
 * - [ObserveCategoryBreakdownUseCase]：分类元信息映射 + 已删分类置底（null 兜底）+
 *   金额降序 + `totalFen>0` 过滤；
 * - [PeriodTrend.balanceFen] = incomeFen − expenseFen（数据 mapper 口径，Row→domain
 *   mapper 为数据层私有，见 09_test_report.md 说明，此处以模型派生与 UseCase 委托覆盖）。
 *
 * 观察路径直注 [LedgerDataStore]（fake）做 `flatMapLatest`（切账本重订阅）。
 */
class ReportUseCaseTest {

    private val period = YearMonth.of(2026, 8)

    // ---- ObserveTrendUseCase：按月/年委托正确 ----

    @Test
    fun `月视图委托 observeDailyTrend 且区间为当月首末日`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val txRepo = FakeTransactionRepository().apply {
            dailyTrend = listOf(
                PeriodTrend(bucket = "2026-08-01", expenseFen = 100L, incomeFen = 50L),
                PeriodTrend(bucket = "2026-08-02", expenseFen = 200L, incomeFen = 100L),
            )
        }

        val trend = ObserveTrendUseCase(txRepo, ledger).observe(period, GRANULARITY.MONTHLY).first()

        assertTrue("月视图应委托 observeDailyTrend", txRepo.dailyTrendInvoked)
        assertFalse("月视图不应触发 observeMonthlyTrend", txRepo.monthlyTrendInvoked)
        assertEquals("2026-08-01" to "2026-08-31", txRepo.lastDailyRange)
        // 三序列映射 + balanceFen = income - expense
        assertEquals(listOf("2026-08-01", "2026-08-02"), trend.buckets)
        assertEquals(listOf(50L, 100L), trend.incomeValues)
        assertEquals(listOf(100L, 200L), trend.expenseValues)
        assertEquals(listOf(-50L, -100L), trend.balanceValues)
        assertFalse(trend.isEmpty)
    }

    @Test
    fun `年视图委托 observeMonthlyTrend 且区间为当年首末日`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val txRepo = FakeTransactionRepository()

        val trend = ObserveTrendUseCase(txRepo, ledger).observe(period, GRANULARITY.YEARLY).first()

        assertTrue("年视图应委托 observeMonthlyTrend", txRepo.monthlyTrendInvoked)
        assertFalse("年视图不应触发 observeDailyTrend", txRepo.dailyTrendInvoked)
        assertEquals("2026-01-01" to "2026-12-31", txRepo.lastMonthlyRange)
        assertTrue("无数据趋势 isEmpty 应为 true（图 1 空态）", trend.isEmpty)
    }

    @Test
    fun `当前账本未初始化 - 发空趋势保活`() = runTest {
        val trend = ObserveTrendUseCase(FakeTransactionRepository(), fakeLedgerDataStore())
            .observe(period, GRANULARITY.MONTHLY).first()
        assertTrue(trend.isEmpty)
    }

    // ---- ObserveBalanceTrendUseCase：runningFold 累计结余 ----

    @Test
    fun `累计结余 runningFold 多桶累加正确`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val txRepo = FakeTransactionRepository().apply {
            dailyTrend = listOf(
                PeriodTrend(bucket = "d1", expenseFen = 100L, incomeFen = 300L), // balance +200
                PeriodTrend(bucket = "d2", expenseFen = 500L, incomeFen = 100L), // balance -400
                PeriodTrend(bucket = "d3", expenseFen = 50L, incomeFen = 200L),  // balance +150
            )
        }

        val balance = ObserveBalanceTrendUseCase(txRepo, ledger)
            .observe(period, GRANULARITY.MONTHLY).first()

        assertEquals(listOf("d1", "d2", "d3"), balance.buckets)
        // runningFold(0): 0+200=200 → 200+(-400)=-200 → -200+150=-50
        assertEquals(listOf(200L, -200L, -50L), balance.cumulativeBalanceValues)
        assertFalse(balance.isEmpty)
    }

    @Test
    fun `无数据时累计结余走势 isEmpty 为 true`() = runTest {
        val balance = ObserveBalanceTrendUseCase(FakeTransactionRepository(), fakeLedgerDataStore())
            .observe(period, GRANULARITY.MONTHLY).first()
        assertTrue(balance.isEmpty)
    }

    // ---- ObserveCategoryBreakdownUseCase：映射/已删兜底/降序/过滤 ----

    @Test
    fun `分类占比 金额降序 且 已删分类置底 null 兜底`() = runTest {
        val ledger = fakeLedgerDataStore("ledger-1")
        val txRepo = FakeTransactionRepository().apply {
            categoryAgg = listOf(
                CategoryAgg(categoryId = "cat-3", totalFen = 3000L),
                CategoryAgg(categoryId = "cat-1", totalFen = 5000L),
                CategoryAgg(categoryId = "cat-deleted", totalFen = 2000L), // 分类已删 → category null
                CategoryAgg(categoryId = "cat-zero", totalFen = 0L),        // 0 金额被过滤
            )
        }
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(
                category("cat-1", "餐饮", order = 1),
                category("cat-3", "购物", order = 5),
            )
        }
        val useCase = ObserveCategoryBreakdownUseCase(
            transactionRepository = txRepo,
            ledgerDataStore = ledger,
            observeCategories = ObserveCategoriesUseCase(ledger, catRepo),
        )

        val slices = useCase.observe(TransactionType.EXPENSE, period, GRANULARITY.MONTHLY).first()

        // 金额降序：cat-1(5000) → cat-3(3000) → cat-deleted(2000)（cat-zero 被过滤）
        assertEquals(listOf("cat-1", "cat-3", "cat-deleted"), slices.map { it.categoryId })
        assertEquals(listOf(5000L, 3000L, 2000L), slices.map { it.totalFen })
        // 分类元信息映射；已删分类 category=null（UI 以 id 兜底）
        assertEquals("cat-1", slices[0].category?.id)
        assertEquals("餐饮", slices[0].category?.name)
        assertEquals("cat-3", slices[1].category?.id)
        assertNull("已删分类 category 应为 null", slices[2].category)
        // 分类聚合委托：type=expense、区间为当月
        assertEquals(TransactionType.EXPENSE, txRepo.lastAggCall?.second)
        assertEquals("2026-08-01" to "2026-08-31", txRepo.lastAggCall?.third)
        assertEquals("ledger-1", txRepo.lastAggCall?.first)
    }

    @Test
    fun `分类占比 当前账本未初始化 - 发空列表保活`() = runTest {
        val useCase = ObserveCategoryBreakdownUseCase(
            transactionRepository = FakeTransactionRepository(),
            ledgerDataStore = fakeLedgerDataStore(),
            observeCategories = ObserveCategoriesUseCase(
                fakeLedgerDataStore(),
                FakeCategoryRepository(),
            ),
        )
        val slices = useCase.observe(TransactionType.EXPENSE, period, GRANULARITY.MONTHLY).first()
        assertEquals(0, slices.size)
    }

    // ---- PeriodTrend.balanceFen（数据 mapper 口径：balanceFen = income - expense） ----

    @Test
    fun `PeriodTrend balanceFen 等于 income 减 expense`() {
        assertEquals(-50L, PeriodTrend("d1", expenseFen = 100L, incomeFen = 50L).balanceFen)
        assertEquals(0L, PeriodTrend("d2", expenseFen = 100L, incomeFen = 100L).balanceFen)
        assertEquals(300L, PeriodTrend("d3", expenseFen = 100L, incomeFen = 400L).balanceFen)
    }

    // ---- ReportTrend.isEmpty 边界 ----

    @Test
    fun `ReportTrend isEmpty 判定`() {
        assertTrue(ReportTrend(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty)
        // 桶非空但收入/支出序列均空 → 视为空态
        assertTrue(
            ReportTrend(listOf("d1"), emptyList(), emptyList(), emptyList()).isEmpty,
        )
        // 收入序列非空 → 非空态
        assertFalse(
            ReportTrend(listOf("d1"), listOf(100L), emptyList(), listOf(100L)).isEmpty,
        )
    }

    // ---- 构造助手 ----

    private fun category(id: String, name: String, order: Int): Category = Category(
        id = id,
        ledgerId = "ledger-1",
        name = name,
        icon = "icon",
        color = "#000000",
        type = CategoryType.EXPENSE,
        isBuiltIn = false,
        order = order,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )
}
