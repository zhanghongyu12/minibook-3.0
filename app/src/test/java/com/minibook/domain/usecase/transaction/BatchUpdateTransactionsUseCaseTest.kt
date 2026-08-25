package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.InvalidTransactionInput
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.category.UpsertUserCategoryRuleUseCase
import com.minibook.testutil.FakeCategoryRepository
import com.minibook.testutil.FakeTransactionRepository
import com.minibook.testutil.FakeUserCategoryRuleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * BatchUpdateTransactionsUseCase 单测（记账页批量改分类 / 批量改类型）：
 * - 空 ids 短路（改分类/改类型均不触库）；
 * - 改分类：透传 updateCategoryByIds，并对「导入 + 有交易对方」的交易逐条 upsert 自学习规则
 *   （手动 / 无交易对方不学习）；
 * - 改类型：分类归位新 type 的「其他」兜底、不触发自学习；缺「其他」抛错零写入。
 */
class BatchUpdateTransactionsUseCaseTest {

    private fun newUseCase(
        txRepo: FakeTransactionRepository,
        catRepo: FakeCategoryRepository,
        ruleRepo: FakeUserCategoryRuleRepository,
    ) = BatchUpdateTransactionsUseCase(
        transactionRepository = txRepo,
        categoryRepository = catRepo,
        upsertUserCategoryRule = UpsertUserCategoryRuleUseCase(ruleRepo),
    )

    private fun tx(
        id: String,
        source: TransactionSource = TransactionSource.MANUAL,
        counterparty: String? = null,
    ) = Transaction(
        id = id,
        ledgerId = LEDGER,
        type = TransactionType.EXPENSE,
        amount = 1000L,
        categoryId = "cat-old",
        date = "2026-08-18",
        createdAt = "2026-08-18T00:00:00.000Z",
        updatedAt = "2026-08-18T00:00:00.000Z",
        source = source,
        counterparty = counterparty,
    )

    private fun category(id: String, name: String, type: CategoryType) = Category(
        id = id,
        ledgerId = LEDGER,
        name = name,
        icon = "ic",
        color = "#000000",
        type = type,
        isBuiltIn = true,
        order = 1,
        createdAt = "2026-08-18T00:00:00.000Z",
        updatedAt = "2026-08-18T00:00:00.000Z",
    )

    @Test
    fun `改分类空列表短路 - 不触库不学习`() = runTest {
        val txRepo = FakeTransactionRepository()
        val ruleRepo = FakeUserCategoryRuleRepository()

        newUseCase(txRepo, FakeCategoryRepository(), ruleRepo)
            .updateCategory(LEDGER, emptyList(), "cat-x")

        assertNull(txRepo.lastUpdateCategoryIds)
        assertTrue(ruleRepo.upsertedRules.isEmpty())
    }

    @Test
    fun `改分类透传并只对导入且有交易对方的交易学习`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            transactionsById = mapOf(
                "t1" to tx("t1", TransactionSource.ALIPAY_IMPORT, "北京面馆"),
                "t2" to tx("t2", TransactionSource.WECHAT_IMPORT, "星巴克"),
                "t3" to tx("t3", TransactionSource.MANUAL, "张三"), // 手动 → 不学习
                "t4" to tx("t4", TransactionSource.ALIPAY_IMPORT, null), // 无交易对方 → 不学习
            )
        }
        val ruleRepo = FakeUserCategoryRuleRepository()

        newUseCase(txRepo, FakeCategoryRepository(), ruleRepo)
            .updateCategory(LEDGER, listOf("t1", "t2", "t3", "t4"), "cat-eat")

        assertEquals(listOf("t1", "t2", "t3", "t4"), txRepo.lastUpdateCategoryIds)
        assertEquals("cat-eat", txRepo.lastUpdateCategoryId)
        // 只学 2 条（t1/t2），keyword=交易对方、source=交易来源、categoryId=新分类。
        assertEquals(2, ruleRepo.upsertedRules.size)
        assertEquals(
            setOf("北京面馆", "星巴克"),
            ruleRepo.upsertedRules.map { it.keyword }.toSet(),
        )
        assertEquals(
            setOf(TransactionSource.ALIPAY_IMPORT, TransactionSource.WECHAT_IMPORT),
            ruleRepo.upsertedRules.map { it.source }.toSet(),
        )
        assertTrue(ruleRepo.upsertedRules.all { it.categoryId == "cat-eat" && it.ledgerId == LEDGER })
    }

    @Test
    fun `改类型归位新类型的其他兜底且不学习`() = runTest {
        val txRepo = FakeTransactionRepository()
        val ruleRepo = FakeUserCategoryRuleRepository()
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(
                category("cat-other-inc", "其他", CategoryType.INCOME),
                category("cat-salary", "工资", CategoryType.INCOME),
            )
        }

        newUseCase(txRepo, catRepo, ruleRepo)
            .updateType(LEDGER, listOf("t1", "t2"), TransactionType.INCOME)

        // 先改 type，再按新 type 的「其他」归位 categoryId。
        assertEquals(listOf("t1", "t2"), txRepo.lastUpdateTypeIds)
        assertEquals(TransactionType.INCOME, txRepo.lastUpdateType)
        assertEquals(listOf("t1", "t2"), txRepo.lastUpdateCategoryIds)
        assertEquals("cat-other-inc", txRepo.lastUpdateCategoryId)
        // 按新类型查询过「其他」兜底。
        assertEquals(CategoryType.INCOME, catRepo.lastObservedType)
        // 改类型不触发自学习。
        assertTrue(ruleRepo.upsertedRules.isEmpty())
    }

    @Test
    fun `改类型缺其他兜底 - 抛错且零写入`() = runTest {
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository().apply {
            categories = listOf(category("cat-salary", "工资", CategoryType.INCOME)) // 无「其他」
        }

        try {
            newUseCase(txRepo, catRepo, FakeUserCategoryRuleRepository())
                .updateType(LEDGER, listOf("t1"), TransactionType.INCOME)
            fail("缺少「其他」兜底时应抛 InvalidTransactionInput")
        } catch (e: InvalidTransactionInput) {
            assertTrue(e.message.orEmpty().contains("其他"))
        }

        // 抛错整体失败：未发生任何写入。
        assertNull(txRepo.lastUpdateTypeIds)
        assertNull(txRepo.lastUpdateCategoryIds)
    }

    @Test
    fun `改类型空列表短路 - 不触库`() = runTest {
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository() // 无任何分类，但空列表在查兜底前短路

        newUseCase(txRepo, catRepo, FakeUserCategoryRuleRepository())
            .updateType(LEDGER, emptyList(), TransactionType.INCOME)

        assertNull(txRepo.lastUpdateTypeIds)
        assertNull(txRepo.lastUpdateCategoryIds)
    }

    private companion object {
        const val LEDGER = "ledger-1"
    }
}
