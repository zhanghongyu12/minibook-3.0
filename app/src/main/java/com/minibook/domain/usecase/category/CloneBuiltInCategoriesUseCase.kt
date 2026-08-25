package com.minibook.domain.usecase.category

import com.minibook.domain.model.BuiltInCategories
import com.minibook.domain.model.Category
import com.minibook.domain.repository.CategoryRepository
import com.minibook.util.TimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * 为指定账本克隆内置分类集（TASK-011），供首启引导（BootstrapUseCase）与新账本创建
 * （TASK-012 CreateLedgerUseCase，在事务内）复用。
 *
 * 对齐 ARCHITECTURE §8.4 / DATABASE §8.3：
 * - **幂等守卫**：`countByLedger(ledgerId)==0` 才 clone（重装/恢复 KeyStore 后不重复刷）；
 * - 每个种子为**该账本独立**生成 UUID（`UUID.randomUUID()`），不跨账本复用，并填
 *   `ledgerId` 与 `createdAt`/`updatedAt`（DATABASE §8.3 / §9.5）；
 * - `isBuiltIn` 恒 true、`type`/`order` 原样透传模板（DATABASE §8.3），「其他」order=99
 *   由模板锁定（DEC-007）。
 *
 * 批量写入走 `CategoryRepository.upsertList`（`REPLACE` 冲突策略，种子/导入幂等语义）。
 */
class CloneBuiltInCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {

    /**
     * 为 [ledgerId] 账本克隆 24 条内置分类（16 支出 + 8 收入，DATABASE §8）。
     *
     * @param ledgerId 目标账本 id；该账本已有分类（`countByLedger>0`）时直接返回（幂等）。
     */
    suspend operator fun invoke(ledgerId: String) {
        // 幂等守卫：已有分类则跳过，避免重复刷（DATABASE §8.3）。
        if (categoryRepository.countByLedger(ledgerId) > 0L) return

        val now = TimeFormatter.nowIso()
        val categories: List<Category> = BuiltInCategories.ALL.map { template ->
            template.copy(
                id = UUID.randomUUID().toString(),
                ledgerId = ledgerId,
                createdAt = now,
                updatedAt = now,
            )
        }
        categoryRepository.upsertList(categories)
    }
}
