package com.minibook.data.mapper

import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.SyncStatus

/**
 * 债务 Entity↔Domain 双向映射（TASK-008），对齐 DATABASE §4.2 / API §10。
 *
 * 映射口径：`amount` 保持 `Long` 分；`date`/`createdAt`/`updatedAt` ISO 字符串透传；
 * kind/direction/syncStatus 在 Entity 为 String，此处与 domain 枚举互转
 * （`fromValue` ↔ `value`），未知值硬失败。
 */
object DebtMapper {

    /** Entity → Domain；kind/direction/syncStatus 由 String 转枚举。 */
    fun toDomain(entity: DebtEntity): Debt = Debt(
        id = entity.id,
        ledgerId = entity.ledgerId,
        partyName = entity.partyName,
        amount = entity.amount,
        kind = requireNotNull(DebtKind.fromValue(entity.kind)) {
            "未知债务种类: ${entity.kind}"
        },
        direction = requireNotNull(DebtDirection.fromValue(entity.direction)) {
            "未知债务方向: ${entity.direction}"
        },
        date = entity.date,
        remark = entity.remark,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        syncStatus = requireNotNull(SyncStatus.fromValue(entity.syncStatus)) {
            "未知同步状态: ${entity.syncStatus}"
        },
    )

    /** Domain → Entity；枚举落盘为其 `value` 常量串。 */
    fun toEntity(domain: Debt): DebtEntity = DebtEntity(
        id = domain.id,
        ledgerId = domain.ledgerId,
        partyName = domain.partyName,
        amount = domain.amount,
        kind = domain.kind.value,
        direction = domain.direction.value,
        date = domain.date,
        remark = domain.remark,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        syncStatus = domain.syncStatus.value,
    )
}
