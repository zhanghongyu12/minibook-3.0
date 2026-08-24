package com.minibook.domain.usecase.category

import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 删除被引用分类并迁移其交易（TASK-015，A-1 采纳方案）。
 *
 * **业务前置校验（本 UseCase 承载，Repository 不承载业务规则，ARCHITECTURE §4.1）**：
 * - 源分类存在、且为自定义（`isBuiltIn=false`，内置禁删 DEC-007）；
 * - 目标分类存在、**同 `type`**、**非自身**（源 id != 目标 id）；
 * - `countTransactionsByCategory(源)>0`（0 引用应走 [DeleteCategoryUseCase] 直接删）。
 *
 * 校验通过后**单调用** [CategoryRepository.deleteCategoryWithMigration]：事务已在
 * Repository 侧经 TransactionRunner 闭合（TASK-009）——「取关联交易 → 逐条重挂目标分类
 * → 删旧分类」整体原子，第 k 条 update 失败 → 全部回滚、无悬空引用（TASK-015 验收）。
 * 本类不承载事务细节（DEC-001）。UI 单调用本类（P-05 迁移流程，TASK-026 接线）。
 *
 * **级联清理（P1-1）**：迁移后仍删源分类，其预算行须一并清空（目标分类不继承源预算）；
 * 故本类在 [TransactionRunner.runInTransaction] 外层事务内，于
 * [CategoryRepository.deleteCategoryWithMigration] 之后追加
 * [BudgetRepository.deleteByCategoryId](源分类)，与迁移+删除整体原子闭合。
 */
class DeleteCategoryWithMigrationUseCase @Inject constructor(
    private val ledgerProvider: LedgerProvider,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRunner: TransactionRunner,
) {

    /**
     * 删除当前账本下 [categoryId] 分类，并将其全部交易迁移到 [targetCategoryId]。
     *
     * @param categoryId 源分类 id（被删除）。
     * @param targetCategoryId 目标分类 id（交易重挂至此）。
     * @throws InvalidInput 源/目标不存在、源为内置、目标不同 type、自身迁移、
     *   无关联交易（应走 [DeleteCategoryUseCase] 直接删）。
     */
    suspend operator fun invoke(categoryId: String, targetCategoryId: String) {
        val ledgerId = ledgerProvider.currentLedgerId()
        // Repository 未暴露 getById，取账本分类快照按 id 定位源/目标原值。
        val categories = categoryRepository.observeByLedger(ledgerId, null).first()

        val source = categories.firstOrNull { it.id == categoryId }
            ?: throw InvalidInput("分类不存在：$categoryId")
        val target = categories.firstOrNull { it.id == targetCategoryId }
            ?: throw InvalidInput("目标分类不存在：$targetCategoryId")

        if (source.isBuiltIn) throw InvalidInput("内置分类不可删除")
        if (source.id == target.id) throw InvalidInput("目标分类不能是自身")
        if (target.type != source.type) {
            throw InvalidInput("目标分类与源分类类型不一致（须同为 ${source.type.value}）")
        }
        if (categoryRepository.countTransactionsByCategory(categoryId) == 0) {
            throw InvalidInput("该分类无关联交易，请直接删除（DeleteCategoryUseCase）")
        }

        // P1-1：迁交易 + 删源分类 + 级联清源分类预算 同一挂起事务闭合；目标分类不继承预算。
        // Room withTransaction 可重入：内层 deleteCategoryWithMigration 事务并入此处外层事务。
        transactionRunner.runInTransaction {
            categoryRepository.deleteCategoryWithMigration(ledgerId, categoryId, targetCategoryId)
            budgetRepository.deleteByCategoryId(categoryId)
        }
    }
}
