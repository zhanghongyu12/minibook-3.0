package com.minibook.domain.usecase.category

import com.minibook.domain.model.BuiltInCategories
import com.minibook.domain.model.Category
import com.minibook.domain.repository.CategoryRepository
import com.minibook.util.TimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 为指定账本**增量补种**缺失的内置分类（F-12 分类校准，B2）。
 *
 * 与 [CloneBuiltInCategoriesUseCase] 的分工：后者 `countByLedger==0` 才整批 clone 24
 * 条种子，**已有分类的账本会被幂等守卫跳过**，因此这次新增的内置分类（美团/转账/缴费/收款/退款）
 * 无法通过 clone 进入既有账本。本 UseCase 不设数量守卫，而是逐条比对：
 * 取账本现有分类按 `(type, name)` 建集合，遍历 [BuiltInCategories.ALL] 找出**缺失**的模板，逐条
 * `copy(id=UUID, ledgerId, createdAt/updatedAt=now)` 补齐，非空则 [CategoryRepository.upsertList]
 * （`REPLACE` 冲突策略，幂等）。
 *
 * 幂等语义：已存在 `(type, name)` 的分类不会被重复插入；缺失集为空时零写入。
 *
 * 选型理由（接入点）：本 UseCase 挂在 [com.minibook.domain.usecase.bill.ImportBillUseCase]
 * 调用起点（而非首启 [com.minibook.domain.usecase.ledger.BootstrapUseCase]），因为 Bootstrap
 * 在「默认账本已存在」时直接返回，永远不再执行，无法覆盖**已存在账本**的增量补种；导入前先
 * ensure 能以最小改动保证「导入前分类齐全」（B2 目标），且稳态下仅一次 `observeByLedger().first()`
 * 读、缺失集为空零写入，成本可忽略。
 */
class EnsureBuiltInCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {

    /**
     * 补齐 [ledgerId] 账本缺失的内置分类（幂等）。
     *
     * @param ledgerId 目标账本 id。
     */
    suspend operator fun invoke(ledgerId: String) {
        val existing = categoryRepository.observeByLedger(ledgerId, null).first()
        val present = existing.map { it.type to it.name }.toSet()

        val now = TimeFormatter.nowIso()
        val missing: List<Category> = BuiltInCategories.ALL
            .filter { (it.type to it.name) !in present }
            .map { template ->
                template.copy(
                    id = UUID.randomUUID().toString(),
                    ledgerId = ledgerId,
                    createdAt = now,
                    updatedAt = now,
                )
            }

        if (missing.isNotEmpty()) {
            categoryRepository.upsertList(missing)
        }
    }
}