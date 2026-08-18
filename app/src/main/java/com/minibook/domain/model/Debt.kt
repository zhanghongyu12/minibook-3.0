package com.minibook.domain.model

/**
 * 债务/借还领域模型，对齐 DATABASE §4.2 / API §10。
 *
 * kind × direction 四态平级：lend/repay × owe_me/i_owe（净额口径见 DATABASE §4.2 表）。
 * amount 为「分」单位 Long；date 为 `yyyy-MM-dd`。
 */
data class Debt(
    val id: String,
    val ledgerId: String,
    val partyName: String,
    val amount: Long,
    val kind: DebtKind,
    val direction: DebtDirection,
    val date: String,
    val remark: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
)