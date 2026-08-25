package com.minibook.domain.usecase.category

import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.UserCategoryRule
import com.minibook.domain.repository.UserCategoryRuleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpsertUserCategoryRuleUseCase 单测（F-12 分类自学习，C2）。
 *
 * 覆盖：正确组装规则（id/createdAt/updatedAt 生成、ledgerId/source/keyword/categoryId 透传）并 upsert。
 */
class UpsertUserCategoryRuleUseCaseTest {

    private class RecordingRuleRepository : UserCategoryRuleRepository {
        var upserted: UserCategoryRule? = null
        override suspend fun upsertRule(rule: UserCategoryRule) { upserted = rule }
        override suspend fun getRules(ledgerId: String): List<UserCategoryRule> = emptyList()
        override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
    }

    @Test
    fun `组装并 upsert 规则`() = runTest {
        val repo = RecordingRuleRepository()
        UpsertUserCategoryRuleUseCase(repo)(
            ledgerId = "ledger-1",
            source = TransactionSource.WECHAT_IMPORT,
            keyword = "北京面馆",
            categoryId = "cat-eat",
        )

        assertNotNull(repo.upserted)
        val rule = repo.upserted!!.let { it }
        assertEquals("ledger-1", rule.ledgerId)
        assertEquals(TransactionSource.WECHAT_IMPORT, rule.source)
        assertEquals("北京面馆", rule.keyword)
        assertEquals("cat-eat", rule.categoryId)
        assertTrue(rule.id.isNotBlank())
        assertTrue(rule.createdAt.isNotBlank())
        assertTrue(rule.updatedAt.isNotBlank())
    }
}