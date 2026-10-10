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

/**
 * 交易来源（F-12 账单导入，DEC-018 / DATABASE §4.1 / API §13.1；F-14 增补短信来源）。
 *
 * 落盘为 `transactions.source` 列（String，默认 `'manual'`，Entity 侧 `@ColumnInfo(defaultValue)`）。
 * 与 [TransactionType] 同径：value 直接暴露给 mapper 做 enum↔String 落盘转换，fromValue 供反向查询。
 *
 * F-14（DEC-025）：新增 [SMS_IMPORT]；`source` 本就是 String 列 → **无 schema 变更**，
 * 备份 JSON（F-04）经 `fromValue` 往返，无需改字段（`BackupJsonMapper` 已按字符串处理）。
 */
enum class TransactionSource(val value: String) {
    MANUAL("manual"),
    WECHAT_IMPORT("wechat_import"),
    ALIPAY_IMPORT("alipay_import"),
    SMS_IMPORT("sms_import");

    companion object {
        fun fromValue(value: String): TransactionSource? = entries.firstOrNull { it.value == value }
    }
}

/**
 * 账单导入跳过原因（F-12，DEC-019 / API §13.1）。
 *
 * 纯领域枚举、不落库（中文文案由 UI 层映射，DEC-020）：
 * - [EXISTS]：externalId 冲突/文件内重复（幂等去重，UseCase 计数，解析器不产出）；
 * - [NEUTRAL]：收/支 = 「不计收支」/「/」/空（微信中性行）；
 * - [CLOSED]：支付宝「交易状态 = 交易关闭」；
 * - [ZERO_AMOUNT]：金额为 0 的支付成功订单（0 元购/退款归零，无需记账）；
 * - [PARSE_ERROR]：时间缺失 / 金额非法(负或非数字) / externalId 单号为空。
 */
enum class ImportSkipReason {
    EXISTS,
    NEUTRAL,
    CLOSED,
    ZERO_AMOUNT,
    PARSE_ERROR,
}
