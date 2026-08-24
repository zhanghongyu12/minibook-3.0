package com.minibook.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 交易实体，对齐 DATABASE §4.1。
 *
 * amount 一律为「分」单位的 Long（无浮点）。
 * date 为 `yyyy-MM-dd` 发生日期；createdAt/updatedAt 为 ISO 8601 完整时间戳。
 * syncStatus 默认 `'local'`，走 StateMachine（Phase 3 云端同步预留）。
 *
 * F-12 四新列（DEC-018，DB version 2 迁移 §7.5）：
 * - `externalId`：账单单号 `alipay:xxx`/`wechat:xxx`，手动记录恒 null；参与唯一索引
 *   `UNIQUE(ledgerId, externalId)` 幂等去重（SQLite 将 NULL 视为互异 ⇒ 手动记录不冲突）。
 * - `counterparty`：交易对方/商家（可空）。
 * - `paymentMethod`：支付方式（空/全空白→null）。
 * - `source`：`manual`/`wechat_import`/`alipay_import`，`@ColumnInfo(defaultValue)` 落数据库级
 *   默认 `'manual'`（与 MIGRATION_1_2 的 `ADD COLUMN ... NOT NULL DEFAULT 'manual'` 一致，
 *   保证 Room `MigrationTestHelper` 校验 defaultValue 不失配）。
 *
 * 字段顺序：syncStatus 之后追加 4 新列，与 MIGRATION_1_2 的 ALTER 追加顺序一一对应
 * （列名比较不依赖物理顺序，但保持「fresh 建表 == 迁移后」的列序一致、便于审阅）。
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["ledgerId", "date"]),
        Index(value = ["ledgerId", "type"]),
        Index(value = ["ledgerId", "categoryId"]),
        Index(value = ["ledgerId", "syncStatus"]),
        Index(value = ["ledgerId", "externalId"], unique = true, name = "index_transactions_ledgerId_externalId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val type: String,
    val amount: Long,
    val categoryId: String,
    val description: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = "local",
    val externalId: String? = null,
    val counterparty: String? = null,
    val paymentMethod: String? = null,
    @ColumnInfo(defaultValue = "'manual'") val source: String = "manual",
)