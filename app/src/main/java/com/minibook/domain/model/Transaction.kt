package com.minibook.domain.model

/**
 * 交易领域模型，对齐 DATABASE §4.1 / API §10 / API §13.2。
 *
 * amount 为「分」单位 Long（无浮点）；date 为 `yyyy-MM-dd` 发生日期；
 * createdAt/updatedAt 为 ISO 8601 完整时间戳。type 为 `expense`/`income` 枚举。
 *
 * F-12 四新字段（DEC-018）：手动记账 `externalId/counterparty/paymentMethod=null`、
 * `source=MANUAL`（默认值即满足，AddTransactionUseCase 零改动）；账单导入
 * `source=WECHAT_IMPORT/ALIPAY_IMPORT`、其余按字段映射填充。
 *
 * `debtId`（DEC-056）：该账单已「转为债务」时指向那条债务的 id，NULL=未转。
 * 关联只记在账单侧（单一事实来源），统计口径据此排除已转债务的账单。
 */
data class Transaction(
    val id: String,
    val ledgerId: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: String,
    val description: String? = null,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
    val externalId: String? = null,
    val counterparty: String? = null,
    val paymentMethod: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val debtId: String? = null,
)