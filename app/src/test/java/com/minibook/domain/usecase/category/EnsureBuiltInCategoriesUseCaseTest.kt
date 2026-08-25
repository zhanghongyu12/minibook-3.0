package com.minibook.domain.usecase.category

import com.minibook.domain.model.BuiltInCategories
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EnsureBuiltInCategoriesUseCase 单测（F-12，B2）。
 *
 * 覆盖：缺少内置分类时按 `(type, name)` 差值补种（ledgerId 正确、id/时间戳非空）；
 * 分类齐全时缺集为空 → 零写入（幂等）。
 */
class EnsureBuiltInCategoriesUseCaseTest {

    private class RecordingCategoryRepository(
        var existing: List<Category> = emptyList(),
    ) : CategoryRepository {
        var upserted: List<Category> = emptyList()
        override suspend fun upsertList(categories: List<Category>) { upserted = categories }
        override fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>> = flowOf(existing)
        override suspend fun insert(category: Category): String = throw NotImplementedError()
        override suspend fun update(category: Category): Unit = throw NotImplementedError()
        override suspend fun deleteById(id: String): Unit = throw NotImplementedError()
        override suspend fun deleteByLedger(ledgerId: String): Unit = throw NotImplementedError()
        override suspend fun countByLedger(ledgerId: String): Long = throw NotImplementedError()
        override suspend fun countTransactionsByCategory(categoryId: String): Int = throw NotImplementedError()
        override suspend fun deleteCategoryWithMigration(ledgerId: String, categoryId: String, targetCategoryId: String): Unit = throw NotImplementedError()
    }

    private fun cat(id: String, name: String, type: CategoryType) = Category(
        id = id,
        ledgerId = "ledger-1",
        name = name,
        icon = "ic",
        color = "#000000",
        type = type,
        isBuiltIn = true,
        order = 1,
        createdAt = "",
        updatedAt = "",
    )

    @Test
    fun `缺少内置分类时补种全部缺失`() = runTest {
        // 已有 1 个「餐饮」→ 缺失 24 - 1 = 23 条被补种。
        val repo = RecordingCategoryRepository(existing = listOf(cat("c1", "餐饮", CategoryType.EXPENSE)))
        EnsureBuiltInCategoriesUseCase(repo)("ledger-1")

        assertEquals(BuiltInCategories.ALL.size - 1, repo.upserted.size)
        assertTrue(repo.upserted.all { it.ledgerId == "ledger-1" })
        assertTrue(repo.upserted.all { it.id.isNotBlank() })
        assertTrue(repo.upserted.all { it.createdAt.isNotBlank() && it.updatedAt.isNotBlank() })
        // 已存在的「餐饮」不重复补种。
        assertTrue(repo.upserted.none { it.name == "餐饮" })
    }

    @Test
    fun `分类齐全时幂等零写入`() = runTest {
        val full = BuiltInCategories.ALL.map { it.copy(id = "id-${it.type}-${it.name}", ledgerId = "ledger-1") }
        val repo = RecordingCategoryRepository(existing = full)
        EnsureBuiltInCategoriesUseCase(repo)("ledger-1")
        assertTrue(repo.upserted.isEmpty())
    }
}