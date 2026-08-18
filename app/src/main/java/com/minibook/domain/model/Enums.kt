package com.minibook.domain.model

/**
 * 领域层枚举集合，取值与 DATABASE §4 常量字符串一一对应（TASK-006）。
 *
 * 每个枚举带 `value` 属性（= Entity 落盘的 String 常量）：
 * - `value` 直接暴露给 TASK-008 mapper 做 enum↔String 落盘转换（Entity 侧为 String 列）。
 * - `fromValue` 提供反向查询，供 mapper / 导入校验复用。
 *
 * 枚举值语义/口径（逐条核对 DATABASE §4）：
 * - TransactionType / CategoryType：`expense` / `income`
 * - DebtKind：`lend` / `repay`
 * - DebtDirection：`owe_me` / `i_owe`
 * - SyncStatus：`local` / `syncing` / `synced` / `conflict`
 */
enum class TransactionType(val value: String) {
    EXPENSE("expense"),
    INCOME("income");

    companion object {
        fun fromValue(value: String): TransactionType? = entries.firstOrNull { it.value == value }
    }
}

enum class CategoryType(val value: String) {
    EXPENSE("expense"),
    INCOME("income");

    companion object {
        fun fromValue(value: String): CategoryType? = entries.firstOrNull { it.value == value }
    }
}

enum class DebtKind(val value: String) {
    LEND("lend"),
    REPAY("repay");

    companion object {
        fun fromValue(value: String): DebtKind? = entries.firstOrNull { it.value == value }
    }
}

enum class DebtDirection(val value: String) {
    OWE_ME("owe_me"),
    I_OWE("i_owe");

    companion object {
        fun fromValue(value: String): DebtDirection? = entries.firstOrNull { it.value == value }
    }
}

enum class SyncStatus(val value: String) {
    LOCAL("local"),
    SYNCING("syncing"),
    SYNCED("synced"),
    CONFLICT("conflict");

    companion object {
        fun fromValue(value: String): SyncStatus? = entries.firstOrNull { it.value == value }
    }
}
