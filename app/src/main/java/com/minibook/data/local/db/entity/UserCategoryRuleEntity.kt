package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 用户自学习归类规则实体（F-12 分类自学习，DB version 3）。
 *
 * 表 `user_category_rules`：`(ledgerId, source, keyword, categoryId)` 唯一索引作 upsert 键
 * （同一账本/来源下「同一关键词 → 同一分类」只存一条；关键词=交易对方全名，分类改到不同值时
 * 因唯一键含 categoryId 会另存一条，保留历史选择）。
 * - `source` 存 [com.minibook.domain.model.TransactionSource].value 字符串（wechat_import/alipay_import），
 *   与 transactions.source 同口径，枚举↔String 转码归 domain mapper；
 * - `keyword` 交易对方全名（子串匹配粒度）；`categoryId` 目标分类 UUID。
 *
 * 唯一索引名 `index_user_category_rules_ledgerId_source_keyword_categoryId` 与
 * [com.minibook.data.local.db.MiniBookDatabase.MIGRATION_2_3] 的 `CREATE UNIQUE INDEX` 逐字一致
 * （Room 迁移校验索引名失配会硬失败）；该索引的 `(ledgerId, source)` 前缀同时服务
 * `queryByLedgerAndSource` / `queryByLedger`，无需额外二级索引。
 */
@Entity(
    tableName = "user_category_rules",
    indices = [
        Index(
            value = ["ledgerId", "source", "keyword", "categoryId"],
            unique = true,
            name = "index_user_category_rules_ledgerId_source_keyword_categoryId",
        ),
    ],
)
@Serializable
data class UserCategoryRuleEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val source: String,
    val keyword: String,
    val categoryId: String,
    val createdAt: String,
    val updatedAt: String,
)