package com.minibook.data.mapper

import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.domain.model.Ledger

/**
 * 账本 Entity↔Domain 双向映射（TASK-008），对齐 DATABASE §4.4 / API §10。
 *
 * 无枚举字段，逐字段原样透传；`isDefault`/`position` 已由 Converter 处理 Boolean↔Int。
 * 账本无 ledgerId 维度（其自身即根作用域，DATABASE §4.4）。
 */
object LedgerMapper {

    /** Entity → Domain。 */
    fun toDomain(entity: LedgerEntity): Ledger = Ledger(
        id = entity.id,
        name = entity.name,
        icon = entity.icon,
        color = entity.color,
        isDefault = entity.isDefault,
        position = entity.position,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /** Domain → Entity。 */
    fun toEntity(domain: Ledger): LedgerEntity = LedgerEntity(
        id = domain.id,
        name = domain.name,
        icon = domain.icon,
        color = domain.color,
        isDefault = domain.isDefault,
        position = domain.position,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )
}
