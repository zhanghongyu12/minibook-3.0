package com.minibook.data.mapper

import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.domain.model.SyncStatus
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType

/**
 * 交易 Entity↔Domain 双向映射（TASK-008），对齐 DATABASE §4.1 / API §10 / API §13.2。
 *
 * 映射口径（DEC-001/§9）：
 * - `amount` 保持 `Long` 分，不转换不丢精度（杜绝浮点）；
 * - `date`/`createdAt`/`updatedAt` ISO 字符串原样透传（日期由 String 列存，Converter 不介入）；
 * - 枚举列（type/syncStatus/source）在 Entity 为 String，此处与 domain 枚举互转：
 *   `EnumType.fromValue(entity.field)`（TASK-006）↔ `domain.field.value`；
 *   未知值以 `requireNotNull` 硬失败，杜绝静默错配（DAO SQL 保证只会写入合法枚举串）。
 * - F-12 新字段（externalId/counterparty/paymentMethod）为可空 String，原样透传；
 *   `source` 与 type/syncStatus 同径互转（DEC-018）。
 */
object TransactionMapper {

    /** Entity → Domain；type/syncStatus/source 由 String 转枚举，未知值抛 [IllegalArgumentException]。 */
    fun toDomain(entity: TransactionEntity): Transaction = Transaction(
        id = entity.id,
        ledgerId = entity.ledgerId,
        type = requireNotNull(TransactionType.fromValue(entity.type)) {
            "未知交易类型: ${entity.type}"
        },
        amount = entity.amount,
        categoryId = entity.categoryId,
        description = entity.description,
        date = entity.date,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        syncStatus = requireNotNull(SyncStatus.fromValue(entity.syncStatus)) {
            "未知同步状态: ${entity.syncStatus}"
        },
        externalId = entity.externalId,
        counterparty = entity.counterparty,
        paymentMethod = entity.paymentMethod,
        source = requireNotNull(TransactionSource.fromValue(entity.source)) {
            "未知交易来源: ${entity.source}"
        },
    )

    /** Domain → Entity；枚举落盘为其 `value` 常量串。 */
    fun toEntity(domain: Transaction): TransactionEntity = TransactionEntity(
        id = domain.id,
        ledgerId = domain.ledgerId,
        type = domain.type.value,
        amount = domain.amount,
        categoryId = domain.categoryId,
        description = domain.description,
        date = domain.date,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        syncStatus = domain.syncStatus.value,
        externalId = domain.externalId,
        counterparty = domain.counterparty,
        paymentMethod = domain.paymentMethod,
        source = domain.source.value,
    )
}