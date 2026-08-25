package com.minibook.domain.usecase.ledger

import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.DebtRepository
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.repository.TransactionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 删除账本（TASK-012，DATABASE §4.4 / ARCHITECTURE §8.4「默认账本不可删除」）。
 *
 * 业务守卫（本 UseCase 已闭合）：
 * - **默认账本拒绝**：目标为默认账本（`isDefault=true`，或 `getDefault()==该账本`）→ 拒绝；
 * - **至少保留一个**：删前账本数 `<=1` → 拒绝（防删空全库）；
 * - 目标不存在 → [LedgerNotFound]（§8.5）。
 *
 * **5 表批删 + 删账本行（TASK-012 已闭合；F-12 增 user_category_rules）**：单事务内按 `ledgerId` 批删
 * transactions / debts / categories / budgets / user_category_rules 五表后再删 Ledger 行（[deleteLedgerAndData]），
 * 任一失败整体回滚（TASK-012 验收：删账本原子性）。依赖的 `deleteByLedger(ledgerId)`
 * 系列方法 + `LedgerRepository.deleteById` 为协调者裁定方案 1（回溯补齐已 commit
 * 接口 + 实现）后新增：`Transaction/Debt/Category/BudgetDataSource.deleteByLedger`
 * （TASK-007）+ 对应 Repository（TASK-009）委托 + `RoomDataSourceImpl` 原始 SQL
 * 实现（TASK-008）——共享方法同时覆盖 `UserCategoryRuleDataSource.deleteByLedger`
 * （F-12 分类自学习规则随账本清空，C5）；`LedgerDataSource.deleteById` / `LedgerDataSourceAdapter`（TASK-008）。
 *
 * 注：删除当前非默认账本后，「当前账本」会悬空——切换职责归调用方（UI 账本管理页），
 * 本 UseCase 不承载（防『无当前账本』态由 UI 在删除前先切换）。
 */
class DeleteLedgerUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val transactionRepository: TransactionRepository,
    private val debtRepository: DebtRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val ledgerRepository: LedgerRepository,
) {

    /**
     * 删除账本。
     *
     * @throws LedgerNotFound 目标账本不存在。
     * @throws IllegalStateException 默认账本 / 唯一账本被业务拒绝。
     */
    suspend operator fun invoke(ledgerId: String) {
        val ledger = ledgerRepository.getById(ledgerId) ?: throw LedgerNotFound("账本不存在：$ledgerId")

        // 默认账本业务拒绝（DATABASE §4.4 / ARCHITECTURE §8.4）。
        if (ledger.isDefault || ledgerRepository.getDefault()?.id == ledgerId) {
            throw IllegalStateException("默认账本不可删除")
        }

        // 至少保留一个账本（删前 count<=1 拒绝）。
        if (ledgerRepository.observeAll().first().size <= 1) {
            throw IllegalStateException("至少需保留一个账本")
        }

        deleteLedgerAndData(ledgerId)
    }

    /**
     * 破坏性删除：单事务清 5 表 + 删 Ledger 行。
     *
     * 编排顺序（DATABASE §4.4）：先按 [ledgerId] 批删 transactions / debts / categories /
     * budgets，最后删 Ledger 行（[LedgerRepository.deleteById]）。整体包进
     * [TransactionRunner.runInTransaction]：任一 delete 失败 → 全部回滚（TASK-012 验收：
     * 删账本原子性，无「业务表清了、账本行仍在」的半删状态）。
     */
    private suspend fun deleteLedgerAndData(ledgerId: String) = transactionRunner.runInTransaction {
        transactionRepository.deleteByLedger(ledgerId)
        debtRepository.deleteByLedger(ledgerId)
        categoryRepository.deleteByLedger(ledgerId)
        budgetRepository.deleteByLedger(ledgerId)
        ledgerRepository.deleteById(ledgerId)
    }
}
