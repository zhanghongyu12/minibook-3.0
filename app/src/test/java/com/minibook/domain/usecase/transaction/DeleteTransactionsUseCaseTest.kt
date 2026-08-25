package com.minibook.domain.usecase.transaction

import com.minibook.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DeleteTransactionsUseCase 单测（记账页批量删除）：
 * - 空列表 → 直接返回 0，**不触达** repository.deleteByIds（避免空 `IN ()` SQL / 无谓事务）；
 * - 非空列表 → 透传 ids 并返回 repository 删除行数（UseCase 不做业务改写）。
 */
class DeleteTransactionsUseCaseTest {

    private fun useCase(repo: FakeTransactionRepository) =
        DeleteTransactionsUseCase(transactionRepository = repo)

    @Test
    fun `空列表直接返回 0 且不触库`() = runTest {
        val repo = FakeTransactionRepository()

        val result = useCase(repo).invoke(emptyList())

        assertEquals(0, result)
        assertNull("空列表不应触达 repository.deleteByIds", repo.lastDeletedIds)
    }

    @Test
    fun `非空列表透传并返回仓库删除行数`() = runTest {
        val repo = FakeTransactionRepository().apply { deleteByIdsResult = 3 }

        val result = useCase(repo).invoke(listOf("t1", "t2", "t3"))

        assertEquals(3, result)
        assertEquals(listOf("t1", "t2", "t3"), repo.lastDeletedIds)
    }
}
