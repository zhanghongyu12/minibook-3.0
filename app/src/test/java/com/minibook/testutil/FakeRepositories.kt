package com.minibook.testutil

import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.model.Budget
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.model.MonthSummary
import com.minibook.domain.model.PeriodTrend
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import com.minibook.domain.model.UserCategoryRule
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.DebtRepository
import com.minibook.domain.repository.TransactionRepository
import com.minibook.domain.repository.UserCategoryRuleRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake [TransactionRepository]：可配置返回数据，并记录最后一次调用的入参
 * （用于验证 UseCase 的过滤器/区间委托透传，TASK-032/TASK-036）。
 *
 * 只实现 UseCase 单测所需的 observe 方法；写/单值路径抛 [NotImplementedError]，
 * 以防被测对象意外走到（部署时暴露漏测）。
 */
class FakeTransactionRepository : TransactionRepository {

    // ==== 可配置返回数据 ====
    var transactions: List<Transaction> = emptyList()
    var dailyTrend: List<PeriodTrend> = emptyList()
    var monthlyTrend: List<PeriodTrend> = emptyList()
    var categoryAgg: List<CategoryAgg> = emptyList()
    var transactionsById: Map<String, Transaction> = emptyMap()

    // ==== 调用记录 ====
    var lastObservedLedgerId: String? = null
    var lastFilter: TransactionFilter? = null
    var dailyTrendInvoked = false
    var monthlyTrendInvoked = false
    var lastDailyRange: Pair<String, String>? = null
    var lastMonthlyRange: Pair<String, String>? = null
    var lastAggCall: Triple<String?, TransactionType?, Pair<String, String>?>? = null
    var lastDeletedIds: List<String>? = null
    var deleteByIdsResult: Int = 0
    var lastUpdateCategoryIds: List<String>? = null
    var lastUpdateCategoryId: String? = null
    var updateCategoryByIdsResult: Int = 0
    var lastUpdateTypeIds: List<String>? = null
    var lastUpdateType: TransactionType? = null
    var updateTypeByIdsResult: Int = 0

    // ---- observe 转发（返回可配置数据 + 记录入参） ----

    override fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>> {
        lastObservedLedgerId = ledgerId
        lastFilter = filter
        return flowOf(transactions)
    }

    override fun observeDailyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>> {
        dailyTrendInvoked = true
        lastDailyRange = start to end
        return flowOf(dailyTrend)
    }

    override fun observeMonthlyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>> {
        monthlyTrendInvoked = true
        lastMonthlyRange = start to end
        return flowOf(monthlyTrend)
    }

    override fun observeCategoryAgg(
        ledgerId: String,
        type: TransactionType,
        start: String,
        end: String,
    ): Flow<List<CategoryAgg>> {
        lastAggCall = Triple(ledgerId, type, start to end)
        return flowOf(categoryAgg)
    }

    // ---- 其余接口方法：本组单测不触达，暴露即漏测 ----

    override suspend fun insert(tx: Transaction): String = throw NotImplementedError()
    override suspend fun update(tx: Transaction) = throw NotImplementedError()
    override suspend fun deleteById(id: String) = throw NotImplementedError()

    override suspend fun deleteByIds(ids: List<String>): Int {
        lastDeletedIds = ids
        return deleteByIdsResult
    }

    override suspend fun updateCategoryByIds(ids: List<String>, categoryId: String): Int {
        lastUpdateCategoryIds = ids
        lastUpdateCategoryId = categoryId
        return updateCategoryByIdsResult
    }

    override suspend fun updateTypeByIds(ids: List<String>, type: TransactionType): Int {
        lastUpdateTypeIds = ids
        lastUpdateType = type
        return updateTypeByIdsResult
    }

    override suspend fun deleteByLedger(ledgerId: String) = throw NotImplementedError()
    override suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary =
        throw NotImplementedError()
    override suspend fun getById(id: String): Transaction? = transactionsById[id]
    override suspend fun countTransactionsByCategory(categoryId: String): Int =
        throw NotImplementedError()
    override suspend fun insertBatch(txs: List<Transaction>): Int = throw NotImplementedError()
    override suspend fun getExistingExternalIds(ledgerId: String): Set<String> = throw NotImplementedError()
}

/**
 * Fake [DebtRepository]：记录最后一次 `observeByLedger` 入参，返回可配置债务列表。
 */
class FakeDebtRepository : DebtRepository {

    var debts: List<Debt> = emptyList()
    var lastObservedLedgerId: String? = null
    var lastFilter: DebtFilter? = null

    override fun observeByLedger(ledgerId: String, filter: DebtFilter): Flow<List<Debt>> {
        lastObservedLedgerId = ledgerId
        lastFilter = filter
        return flowOf(debts)
    }

    override suspend fun insert(debt: Debt): String = throw NotImplementedError()
    override suspend fun update(debt: Debt) = throw NotImplementedError()
    override suspend fun deleteById(id: String) = throw NotImplementedError()
    override suspend fun deleteByLedger(ledgerId: String) = throw NotImplementedError()
    override suspend fun netByParty(ledgerId: String): Map<String, Long> = throw NotImplementedError()
}

/**
 * Fake [BudgetRepository]：返回可配置的月度预算列表（总额 + 分类），并记录最近一次
 * `observeByMonth` 的账本/月份。
 */
class FakeBudgetRepository : BudgetRepository {

    var budgets: List<Budget> = emptyList()
    var lastObservedLedgerId: String? = null
    var lastObservedMonth: YearMonth? = null
    var lastDeletedCategoryId: String? = null

    /** 最近一次 `clearTotalBudget(ledgerId, month)` 记录（F-15x 单测用）。 */
    var lastClearedTotal: Pair<String, YearMonth>? = null

    /** 最近一次 `clearCategoryBudget(ledgerId, month, categoryId)` 记录（F-15w 单测用）。 */
    var lastClearedCategory: Triple<String, YearMonth, String>? = null

    override fun observeByMonth(ledgerId: String, month: YearMonth): Flow<List<Budget>> {
        lastObservedLedgerId = ledgerId
        lastObservedMonth = month
        return flowOf(budgets)
    }

    override suspend fun setTotalBudget(ledgerId: String, month: YearMonth, amount: Long) =
        throw NotImplementedError()
    override suspend fun setCategoryBudget(ledgerId: String, month: YearMonth, categoryId: String, amount: Long) =
        throw NotImplementedError()
    override suspend fun clearTotalBudget(ledgerId: String, month: YearMonth) {
        lastClearedTotal = ledgerId to month
    }
    override suspend fun clearCategoryBudget(ledgerId: String, month: YearMonth, categoryId: String) {
        lastClearedCategory = Triple(ledgerId, month, categoryId)
    }
    override suspend fun deleteByLedger(ledgerId: String) = throw NotImplementedError()
    override suspend fun deleteByCategoryId(categoryId: String) {
        lastDeletedCategoryId = categoryId
    }
}

/**
 * Fake [CategoryRepository]：返回可配置分类流（含按 type 过滤的记录），供预算进度/分类占比用。
 */
class FakeCategoryRepository : CategoryRepository {

    var categories: List<Category> = emptyList()
    var lastObservedLedgerId: String? = null
    var lastObservedType: CategoryType? = null

    // ---- 写/单值路径调用记录（P1-1 删除分类级联测试用）----
    var lastDeletedId: String? = null
    var countTransactionsByCategoryResult: Int = 0
    var lastCountedCategoryId: String? = null
    var lastMigrationCall: Triple<String, String, String>? = null

    override fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>> {
        lastObservedLedgerId = ledgerId
        lastObservedType = type
        return flowOf(categories)
    }

    override suspend fun upsertList(categories: List<Category>) = throw NotImplementedError()
    override suspend fun insert(category: Category): String = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun deleteById(id: String) {
        lastDeletedId = id
    }

    override suspend fun deleteByLedger(ledgerId: String) = throw NotImplementedError()
    override suspend fun countByLedger(ledgerId: String): Long = throw NotImplementedError()
    override suspend fun countTransactionsByCategory(categoryId: String): Int {
        lastCountedCategoryId = categoryId
        return countTransactionsByCategoryResult
    }

    override suspend fun deleteCategoryWithMigration(ledgerId: String, categoryId: String, targetCategoryId: String) {
        lastMigrationCall = Triple(ledgerId, categoryId, targetCategoryId)
    }
}

/**
 * Fake [TransactionRunner]：直接执行块（JVM 单测无 Room，不真正开 DB 事务），并统计
 * 事务包装次数。供删除分类级联（P1-1）/删除账本等「UseCase 外层事务多步编排」单测使用。
 */
class FakeTransactionRunner : TransactionRunner {

    var transactionsRun = 0
        private set

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        transactionsRun++
        return block()
    }
}

/**
 * Fake [UserCategoryRuleRepository]：记录 upsert 的规则（供批量改分类自学习单测断言），
 * 返回可配置的规则列表（供导入匹配测试复用）。
 */
class FakeUserCategoryRuleRepository : UserCategoryRuleRepository {

    var rules: List<UserCategoryRule> = emptyList()
    val upsertedRules: MutableList<UserCategoryRule> = mutableListOf()

    override suspend fun upsertRule(rule: UserCategoryRule) {
        upsertedRules += rule
    }

    override suspend fun getRules(ledgerId: String): List<UserCategoryRule> = rules

    override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
}
