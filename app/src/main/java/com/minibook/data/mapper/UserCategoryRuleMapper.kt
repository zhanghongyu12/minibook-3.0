package com.minibook.data.mapper

import com.minibook.data.local.db.entity.UserCategoryRuleEntity
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.UserCategoryRule

/**
 * 用户自学习归类规则 Entity↔Domain 双向映射（F-12 分类自学习）。
 *
 * `source` 在 Entity 为 String、domain 为 [TransactionSource]（与 TransactionMapper 同径）：
 * 读侧 `requireNotNull(TransactionSource.fromValue(...))` 硬失败，写侧落 `.value` 常量串。
 */
object UserCategoryRuleMapper {

    fun toDomain(entity: UserCategoryRuleEntity): UserCategoryRule = UserCategoryRule(
        id = entity.id,
        ledgerId = entity.ledgerId,
        source = requireNotNull(TransactionSource.fromValue(entity.source)) {
            "未知交易来源: ${entity.source}"
        },
        keyword = entity.keyword,
        categoryId = entity.categoryId,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    fun toEntity(domain: UserCategoryRule): UserCategoryRuleEntity = UserCategoryRuleEntity(
        id = domain.id,
        ledgerId = domain.ledgerId,
        source = domain.source.value,
        keyword = domain.keyword,
        categoryId = domain.categoryId,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )
}