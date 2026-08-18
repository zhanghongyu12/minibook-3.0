package com.minibook.data.mapper

import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.domain.model.Budget

/**
 * 预算 Entity↔Domain 双向映射（TASK-008），对齐 DATABASE §4.5 / API §10。
 *
 * 无枚举字段，逐字段原样透传；`month` 为 `yyyy-MM` 字符串，`amount` 为 Long 分。
 * `categoryId` null = 月度总预算，非 null = 分类预算（DEC-008）。
 */
object BudgetMapper {

    /** Entity → Domain。 */
    fun toDomain(entity: BudgetEntity): Budget = Budget(
        id = entity.id,
        ledgerId = entity.ledgerId,
        month = entity.month,
        categoryId = entity.categoryId,
        amount = entity.amount,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /** Domain → Entity。 */
    fun toEntity(domain: Budget): BudgetEntity = BudgetEntity(
        id = domain.id,
        ledgerId = domain.ledgerId,
        month = domain.month,
        categoryId = domain.categoryId,
        amount = domain.amount,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )
}
