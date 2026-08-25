package com.minibook.domain.usecase.bill

import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.local.bill.BillParserFactory
import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.ImportSkipReason
import com.minibook.domain.model.MonthSummary
import com.minibook.domain.model.ParseSkippedRow
import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.PeriodTrend
import com.minibook.domain.model.SyncStatus
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.model.UserCategoryRule
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.UserCategoryRuleRepository
import com.minibook.domain.usecase.category.EnsureBuiltInCategoriesUseCase
import com.minibook.testutil.FakeTransactionRunner
import java.io.StringReader
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ImportBillUseCase 编排单测（TASK-043，DEC-015/019，API §13.6）——全 fakes，无 Room/Android。
 *
 * 覆盖：成功导入（自动分类命中 + 未命中归「其他」）；幂等去重（既有 externalId 相交 +
 * 文件内重复 → existing）；全跳过计数（neutral/closed）；工厂判不出 → unrecognized；
 * 「其他」兜底缺失 → 抛异常零落库（事务未提交）；insertBatch 写异常 → 异常上抛且零插入回滚。
 */
class ImportBillUseCaseTest {

    // ---------- fakes ----------

    private class FakeTransactionDataSource : TransactionDataSource {
        var existingExternalIds: Set<String> = emptySet()
        var inserted: List<Transaction> = emptyList()
        var insertBatchResult: Int? = null // null = 全部插入成功
        var insertBatchError: RuntimeException? = null // 非 null = insertBatch 抛异常（模拟写异常）

        override suspend fun insertBatch(txs: List<Transaction>): Int {
            insertBatchError?.let { throw it }
            inserted = txs
            return insertBatchResult ?: txs.size
        }

        override suspend fun getExistingExternalIds(ledgerId: String): Set<String> = existingExternalIds

        override suspend fun insert(tx: Transaction): String = throw NotImplementedError()
        override suspend fun update(tx: Transaction): Unit = throw NotImplementedError()
        override suspend fun deleteById(id: String): Unit = throw NotImplementedError()
        override suspend fun deleteByIds(ids: List<String>): Int = throw NotImplementedError()
        override suspend fun updateCategoryByIds(ids: List<String>, categoryId: String): Int = throw NotImplementedError()
        override suspend fun updateTypeByIds(ids: List<String>, type: TransactionType): Int = throw NotImplementedError()
        override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
        override fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeDailyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>> = throw NotImplementedError()
        override fun observeMonthlyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>> = throw NotImplementedError()
        override fun observeCategoryAgg(ledgerId: String, type: TransactionType, start: String, end: String): Flow<List<CategoryAgg>> = throw NotImplementedError()
        override suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary = throw NotImplementedError()
        override suspend fun getById(id: String): Transaction? = throw NotImplementedError()
        override suspend fun countTransactionsByCategory(categoryId: String): Int = throw NotImplementedError()
    }

    private class FakeCategoryDataSource : CategoryDataSource {
        var categories: List<Category> = emptyList()

        override fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>> = flowOf(categories)
        override suspend fun upsertList(categories: List<Category>): Unit = throw NotImplementedError()
        override suspend fun insert(category: Category): String = throw NotImplementedError()
        override suspend fun update(category: Category): Unit = throw NotImplementedError()
        override suspend fun deleteById(id: String): Unit = throw NotImplementedError()
        override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
        override suspend fun countByLedger(ledgerId: String): Long = throw NotImplementedError()
        override suspend fun countTransactionsByCategory(categoryId: String): Int = throw NotImplementedError()
    }

    /** 供 [EnsureBuiltInCategoriesUseCase] 使用的极简 CategoryRepository：只实现 ensure 读取/写入路径，其余抛异常。 */
    private class FakeCategoryRepositoryForEnsure : CategoryRepository {
        override suspend fun upsertList(categories: List<Category>) = Unit
        override fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun insert(category: Category): String = throw NotImplementedError()
        override suspend fun update(category: Category): Unit = throw NotImplementedError()
        override suspend fun deleteById(id: String): Unit = throw NotImplementedError()
        override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
        override suspend fun countByLedger(ledgerId: String): Long = throw NotImplementedError()
        override suspend fun countTransactionsByCategory(categoryId: String): Int = throw NotImplementedError()
        override suspend fun deleteCategoryWithMigration(ledgerId: String, categoryId: String, targetCategoryId: String): Unit = throw NotImplementedError()
    }

    /** 极简 [UserCategoryRuleRepository]：可配置返回规则，upsert 记录入参。 */
    private class FakeUserCategoryRuleRepository(
        var rules: List<UserCategoryRule> = emptyList(),
    ) : UserCategoryRuleRepository {
        var upserted: List<UserCategoryRule> = emptyList()
        override suspend fun upsertRule(rule: UserCategoryRule) { upserted = upserted + rule }
        override suspend fun getRules(ledgerId: String): List<UserCategoryRule> = rules
        override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
    }

    private class FakeBillParser(
        override val format: TransactionSource,
        private val result: BillParseResult,
    ) : BillParser {
        override fun parse(input: BillSourceInput): BillParseResult = result
    }

    private class FakeBillParserFactory(private val parser: BillParser?) : BillParserFactory {
        override fun resolve(displayName: String?, magicBytes: ByteArray?): BillParser? = parser
    }

    private class FakePreparedBillFile(
        override val displayName: String? = "bill.csv",
    ) : PreparedBillFile {
        override val input: BillSourceInput = BillSourceInput.Csv(StringReader(""))
        override val magicBytes: ByteArray? = null
        override fun close() = Unit
    }

    private class FakeBillFilePreparer(
        private val handle: PreparedBillFile = FakePreparedBillFile(),
    ) : BillFilePreparer {
        override fun prepare(uriString: String): PreparedBillFile = handle
    }

    // ---------- fixtures ----------

    private fun category(id: String, name: String, type: CategoryType) = Category(
        id = id,
        ledgerId = LEDGER,
        name = name,
        icon = "ic",
        color = "#000000",
        type = type,
        isBuiltIn = true,
        order = 1,
        createdAt = "",
        updatedAt = "",
    )

    private fun row(
        externalId: String,
        amountFen: Long,
        type: TransactionType = TransactionType.EXPENSE,
        platformCategory: String? = null,
        counterparty: String? = null,
    ) = ParsedBillRow(
        date = "2026-08-20",
        type = type,
        amountFen = amountFen,
        counterparty = counterparty,
        paymentMethod = null,
        description = "desc",
        externalId = externalId,
        platformCategory = platformCategory,
    )

    private fun useCase(
        txDs: FakeTransactionDataSource,
        catDs: FakeCategoryDataSource,
        parser: BillParser?,
        runner: TransactionRunner = FakeTransactionRunner(),
        rules: List<UserCategoryRule> = emptyList(),
    ): ImportBillUseCase = ImportBillUseCase(
        transactionRunner = runner,
        filePreparer = FakeBillFilePreparer(),
        parserFactory = FakeBillParserFactory(parser),
        categoryDataSource = catDs,
        transactionDataSource = txDs,
        ensureBuiltInCategories = EnsureBuiltInCategoriesUseCase(FakeCategoryRepositoryForEnsure()),
        userCategoryRuleRepository = FakeUserCategoryRuleRepository(rules),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    // ---------- tests ----------

    @Test
    fun `成功导入 - 自动分类命中与未命中归其他`() = runTest {
        val txDs = FakeTransactionDataSource()
        val catDs = FakeCategoryDataSource().apply {
            categories = listOf(
                category("cat-eat", "餐饮", CategoryType.EXPENSE),
                category("cat-other-exp", "其他", CategoryType.EXPENSE),
                category("cat-other-inc", "其他", CategoryType.INCOME),
            )
        }
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Success(
                rows = listOf(
                    row("alipay:1", 100, platformCategory = "餐饮美食"),
                    row("alipay:2", 200, platformCategory = "退款"),
                ),
                skipped = emptyList(),
            ),
        )

        val result = useCase(txDs, catDs, parser)("content://bill.csv", LEDGER)

        assertEquals(2, result.success)
        assertEquals(0, result.existing)
        assertFalse(result.unrecognized)
        assertEquals(2, txDs.inserted.size)
        assertEquals("cat-eat", txDs.inserted[0].categoryId)
        assertEquals(TransactionSource.ALIPAY_IMPORT, txDs.inserted[0].source)
        assertEquals("alipay:1", txDs.inserted[0].externalId)
        assertEquals("2026-08-20", txDs.inserted[0].date)
        assertEquals(100L, txDs.inserted[0].amount)
        assertEquals(SyncStatus.LOCAL, txDs.inserted[0].syncStatus)
        // 未命中映射 + 无「其他」时走 expense「其他」锚点。
        assertEquals("cat-other-exp", txDs.inserted[1].categoryId)
    }

    @Test
    fun `幂等去重 - 既有相交与文件内重复均计已存在`() = runTest {
        val txDs = FakeTransactionDataSource().apply {
            existingExternalIds = setOf("alipay:1")
        }
        val catDs = FakeCategoryDataSource().apply {
            categories = listOf(category("cat-other-exp", "其他", CategoryType.EXPENSE))
        }
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Success(
                rows = listOf(
                    row("alipay:1", 100), // 既有 → existing
                    row("alipay:2", 200), // 新增
                    row("alipay:2", 200), // 文件内重复 → existing
                    row("alipay:3", 300), // 新增
                ),
                skipped = emptyList(),
            ),
        )

        val result = useCase(txDs, catDs, parser)("content://bill.csv", LEDGER)

        assertEquals(2, result.success)
        assertEquals(2, result.existing)
        assertEquals(listOf("alipay:2", "alipay:3"), txDs.inserted.map { it.externalId })
    }

    @Test
    fun `全跳过 - 汇总 neutral closed zeroAmount 不落库`() = runTest {
        val txDs = FakeTransactionDataSource()
        val catDs = FakeCategoryDataSource()
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Success(
                rows = emptyList(),
                skipped = listOf(
                    ParseSkippedRow(1, ImportSkipReason.NEUTRAL, "收/支=不计收支"),
                    ParseSkippedRow(2, ImportSkipReason.NEUTRAL, "收/支=/"),
                    ParseSkippedRow(3, ImportSkipReason.CLOSED, "交易状态=交易关闭"),
                    ParseSkippedRow(4, ImportSkipReason.ZERO_AMOUNT, "金额为 0"),
                ),
            ),
        )

        val result = useCase(txDs, catDs, parser)("content://bill.csv", LEDGER)

        assertEquals(0, result.success)
        assertEquals(0, result.existing)
        assertEquals(2, result.neutral)
        assertEquals(1, result.closed)
        assertEquals(1, result.zeroAmount)
        assertTrue(txDs.inserted.isEmpty())
    }

    @Test
    fun `自学习规则命中优先于类型映射`() = runTest {
        val txDs = FakeTransactionDataSource()
        val catDs = FakeCategoryDataSource().apply {
            categories = listOf(
                category("cat-eat", "餐饮", CategoryType.EXPENSE),
                category("cat-learn", "交通", CategoryType.EXPENSE),
                category("cat-other-exp", "其他", CategoryType.EXPENSE),
            )
        }
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Success(
                // 平台分类「餐饮美食」本应映射「餐饮」，但对手方「北京面馆」命中自学习规则 keyword「面馆」→ 交通。
                rows = listOf(row("alipay:1", 100, platformCategory = "餐饮美食", counterparty = "北京面馆")),
                skipped = emptyList(),
            ),
        )
        val rules = listOf(
            UserCategoryRule(
                id = "r1",
                ledgerId = LEDGER,
                source = TransactionSource.ALIPAY_IMPORT,
                keyword = "面馆",
                categoryId = "cat-learn",
                createdAt = "2026-08-01T00:00:00Z",
                updatedAt = "2026-08-01T00:00:00Z",
            ),
        )

        val result = useCase(txDs, catDs, parser, rules = rules)("content://bill.csv", LEDGER)

        assertEquals(1, result.success)
        assertEquals("cat-learn", txDs.inserted[0].categoryId)
    }

    @Test
    fun `工厂判不出格式 - 未识别零落库`() = runTest {
        val txDs = FakeTransactionDataSource()
        val catDs = FakeCategoryDataSource()
        val result = useCase(txDs, catDs, parser = null)("content://bill.csv", LEDGER)

        assertTrue(result.unrecognized)
        assertEquals(0, result.success)
        assertTrue(txDs.inserted.isEmpty())
    }

    @Test
    fun `解析器 Unrecognized 透传 - 未识别零落库`() = runTest {
        val txDs = FakeTransactionDataSource()
        val catDs = FakeCategoryDataSource()
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Unrecognized("未找到表头"),
        )
        val result = useCase(txDs, catDs, parser)("content://bill.csv", LEDGER)

        assertTrue(result.unrecognized)
        assertTrue(txDs.inserted.isEmpty())
    }

    @Test
    fun `缺少其他兜底分类 - 整体失败零落库`() = runTest {
        val txDs = FakeTransactionDataSource()
        val catDs = FakeCategoryDataSource().apply {
            categories = listOf(category("cat-eat", "餐饮", CategoryType.EXPENSE))
        }
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Success(
                rows = listOf(row("alipay:1", 100, platformCategory = "退款")),
                skipped = emptyList(),
            ),
        )

        var thrown: IllegalStateException? = null
        try {
            useCase(txDs, catDs, parser)("content://bill.csv", LEDGER)
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals(0, txDs.inserted.size)
        assertTrue(thrown!!.message!!.contains("其他")) // 兜底缺失提示含「其他」
    }

    @Test
    fun `insertBatch 写异常 - 异常上抛且零插入整体回滚`() = runTest {
        val txDs = FakeTransactionDataSource().apply {
            insertBatchError = IllegalStateException("写入失败")
        }
        val catDs = FakeCategoryDataSource().apply {
            categories = listOf(category("cat-other-exp", "其他", CategoryType.EXPENSE))
        }
        val parser = FakeBillParser(
            format = TransactionSource.ALIPAY_IMPORT,
            result = BillParseResult.Success(
                rows = listOf(row("alipay:1", 100)),
                skipped = emptyList(),
            ),
        )

        var thrown: IllegalStateException? = null
        try {
            useCase(txDs, catDs, parser)("content://bill.csv", LEDGER)
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("写入失败"))
        assertTrue("写异常不得残留插入", txDs.inserted.isEmpty()) // 事务回滚 → 零插入
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}