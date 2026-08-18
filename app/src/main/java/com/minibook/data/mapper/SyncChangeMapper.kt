package com.minibook.data.mapper

import com.minibook.data.local.db.entity.SyncChangeEntity
import com.minibook.domain.model.SyncChange

/**
 * 同步变更 Entity↔Domain 双向映射（TASK-008），对齐 DATABASE §4.6 / API §10。
 *
 * Phase 3 云端出口预留，本期只建表不写数据。entityType/action 在 domain 层
 * 同样为 String（TASK-006 枚举未覆盖该类），故逐字段原样透传，无枚举转换。
 */
object SyncChangeMapper {

    /** Entity → Domain。 */
    fun toDomain(entity: SyncChangeEntity): SyncChange = SyncChange(
        id = entity.id,
        ledgerId = entity.ledgerId,
        entityType = entity.entityType,
        entityId = entity.entityId,
        action = entity.action,
        timestamp = entity.timestamp,
        data = entity.data,
    )

    /** Domain → Entity。 */
    fun toEntity(domain: SyncChange): SyncChangeEntity = SyncChangeEntity(
        id = domain.id,
        ledgerId = domain.ledgerId,
        entityType = domain.entityType,
        entityId = domain.entityId,
        action = domain.action,
        timestamp = domain.timestamp,
        data = domain.data,
    )
}
