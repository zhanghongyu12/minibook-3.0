package com.minibook.util

import kotlinx.serialization.Serializable

/**
 * version 1 备份 JSON DTO（TASK-016，API §11 / DEC-004 / DEC-005 / DEC-006）。
 *
 * 顶层结构对齐 API §11.1：5 数组 + `version=1` + `scope="all"`，**不含 syncChanges**
 * （同步簿记不随全量迁移，API §11.1 尾部说明）；`budgets` 本期恒空（Phase 2 落库后导出，
 * API §11.1 注）。导出为明文 JSON（DEC-005，人工可 inspect）。
 *
 * **legacy 兼容（DEC-004 / D-3）**：[ledgers] **可空**——导入检测顶层缺 `ledgers` 字段
 * （反序列化为 null）即视为旧版（Taro）JSON，导入到默认账本并做时间归一化
 * （API §11.1 legacy 兼容注）。
 *
 * DTO 基础类型约定（API §11.2 / 任务约束）：
 * - `amount` 一律「分」`Long`（不转不丢精度，DATABASE §9）；
 * - `type`/`kind`/`direction`/`syncStatus` 取领域枚举 `.value` 的 String
 *   （enum `.value` 即 Entity 落盘常量，TASK-006 Enums.kt）；
 * - `date` 为 ISO 日期 String（`yyyy-MM-dd`）；legacy 可为非 ISO 时间戳（epoch 毫秒等），
 *   导入时经 [BackupJsonMapper.normalizeDate] 归一化；
 * - 可空/可选字段带默认值，向后兼容（新字段缺失不破坏旧文件解码）。
 *
 * 注：条目 `syncStatus` 供导出/可读（API §11.2 尾部说明条目不含 syncStatus，写入统一 local；
 * 本 DTO 按任务约束保留该字段，导入时忽略之恒写 `local`）。
 */
@Serializable
data class BackupV1(
    val version: Int = 1,
    val scope: String = "all",
    val ledgers: List<LedgerDto>? = null,
    val categories: List<CategoryDto>,
    val transactions: List<TransactionDto>,
    val debts: List<DebtDto>,
    val budgets: List<BudgetDto> = emptyList(),
)

/** 账本条目（API §11.2 Ledger）：`isDefault` 是否存在默认账本，`position` 缺省 0。 */
@Serializable
data class LedgerDto(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean,
    val position: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** 分类条目（API §11.2 Category）：`type` ∈ `expense`/`income`；`isBuiltIn` 内置标记。 */
@Serializable
data class CategoryDto(
    val id: String,
    val ledgerId: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,
    val isBuiltIn: Boolean,
    val order: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * 交易条目（API §11.2 Transaction）：`type` ∈ `expense`/`income`，`amount` 正整分。
 *
 * F-12 四新字段（DEC-018 / P1-1）：`externalId`/`counterparty`/`paymentMethod`/`source`
 * 随备份往返，避免备份还原丢失来源徽标/对方/支付方式/幂等判据。后三者可空透传；
 * `source` 亦为可空 String——旧备份（v1 早期）无该键时按「缺省」处理为 `manual`
 * （[BackupJsonMapper] toDomain 兜底）。
 */
@Serializable
data class TransactionDto(
    val id: String,
    val ledgerId: String,
    val type: String,
    val amount: Long,
    val categoryId: String,
    val description: String? = null,
    val date: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncStatus: String = "local",
    val externalId: String? = null,
    val counterparty: String? = null,
    val paymentMethod: String? = null,
    val source: String? = null,
)

/** 债务条目（API §11.2 Debt）：`kind` ∈ `lend`/`repay`，`direction` ∈ `owe_me`/`i_owe`。 */
@Serializable
data class DebtDto(
    val id: String,
    val ledgerId: String,
    val partyName: String,
    val amount: Long,
    val kind: String,
    val direction: String,
    val date: String,
    val remark: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncStatus: String = "local",
)

/**
 * 预算条目（API §11.2 Budget）：`month` 为 `yyyy-MM`；`categoryId` 为 null=月度总预算，
 * 非 null=分类预算；`amount` ≥0。本期恒空，Phase 2 落库后导出。
 */
@Serializable
data class BudgetDto(
    val id: String,
    val ledgerId: String,
    val month: String,
    val categoryId: String? = null,
    val amount: Long,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
