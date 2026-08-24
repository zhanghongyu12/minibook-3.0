package com.minibook.domain.usecase.category

import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 删除分类（TASK-015，DEC-007 直接删路径）。
 *
 * 守卫（业务层拒绝，数据源 `isBuiltIn=0` SQL 守卫另兜底）：
 * - **内置禁删**（`isBuiltIn=true`）：DEC-007「内置分类不可删」；
 * - **无关联交易**：`countTransactionsByCategory(id)==0` 才允许 `deleteById`；
 *   有关联（>0）→ 本 UseCase 直接拒绝并提示走迁移流程
 *   （[DeleteCategoryWithMigrationUseCase]，UI 依据该异常切换迁移交互，P-05）。
 *
 * Repository 未暴露 `getById`，以 `observeByLedger(ledgerId, null)` 快照按 id 定位原值。
 *
 * **级联清理（P1-1）**：删除分类后其预算行若不清会在 `budgets` 表成孤儿、从预算进度
 * 静默消失；故删除动作在 [TransactionRunner.runInTransaction] 事务内先
 * `categoryRepository.deleteById` 再 `budgetRepository.deleteByCategoryId`，原子闭合。
 */
class DeleteCategoryUseCase @Inject constructor(
    private val ledgerProvider: LedgerProvider,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRunner: TransactionRunner,
) {

    /**
     * 删除当前账本下 [categoryId] 自定义分类（仅限无关联交易）。
     *
     * @throws InvalidInput 分类不存在 / 内置分类 / 存在关联交易（提示走迁移）。
     */
    suspend operator fun invoke(categoryId: String) {
        val ledgerId = ledgerProvider.currentLedgerId()
        val category = categoryRepository.observeByLedger(ledgerId, null).first()
            .firstOrNull { it.id == categoryId }
            ?: throw InvalidInput("分类不存在：$categoryId")

        // 内置禁删（DEC-007 业务层拒绝；SQL isBuiltIn=0 守卫兜底）。
        if (category.isBuiltIn) throw InvalidInput("内置分类不可删除")

        // 关联交易检查：>0 须走迁移（A-1），本 UseCase 不迁移、直接拒绝。
        val refCount = categoryRepository.countTransactionsByCategory(categoryId)
        if (refCount > 0) {
            throw InvalidInput("该分类已有 $refCount 笔交易引用，请走迁移流程删除")
        }

        // P1-1：删分类 + 级联清其预算 同一挂起事务闭合（防分类删除后预算成孤儿静默丢失）。
        // Room withTransaction 可重入：若内层 deleteById 自带事务，会并入此处外层事务。
        transactionRunner.runInTransaction {
            categoryRepository.deleteById(categoryId)
            budgetRepository.deleteByCategoryId(categoryId)
        }
    }
}
