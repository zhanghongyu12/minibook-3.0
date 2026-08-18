package com.minibook.data.mapper

import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType

/**
 * 分类 Entity↔Domain 双向映射（TASK-008），对齐 DATABASE §4.3 / API §10。
 *
 * 映射口径：`isBuiltIn`/`order` 原样透传（Boolean↔INTEGER 已由 [com.minibook.data.local.db.Converter] 处理）；
 * type 在 Entity 为 String，此处与 [CategoryType] 互转，未知值硬失败。
 * 分类无 syncStatus（不参与增量同步）。
 */
object CategoryMapper {

    /** Entity → Domain；type 由 String 转枚举。 */
    fun toDomain(entity: CategoryEntity): Category = Category(
        id = entity.id,
        ledgerId = entity.ledgerId,
        name = entity.name,
        icon = entity.icon,
        color = entity.color,
        type = requireNotNull(CategoryType.fromValue(entity.type)) {
            "未知分类类型: ${entity.type}"
        },
        isBuiltIn = entity.isBuiltIn,
        order = entity.order,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /** Domain → Entity；枚举落盘为其 `value` 常量串。 */
    fun toEntity(domain: Category): CategoryEntity = CategoryEntity(
        id = domain.id,
        ledgerId = domain.ledgerId,
        name = domain.name,
        icon = domain.icon,
        color = domain.color,
        type = domain.type.value,
        isBuiltIn = domain.isBuiltIn,
        order = domain.order,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )
}
