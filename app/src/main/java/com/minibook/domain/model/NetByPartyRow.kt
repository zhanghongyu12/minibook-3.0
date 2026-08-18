package com.minibook.domain.model

/**
 * 按对象净额领域模型，对齐 API §4（净额口径 DATABASE §4.2）。
 *
 * net 为「分」单位 Long：正值=对方欠我（借出+、我还款+），负值=我欠对方
 * （借入−、收到还款−），0=两清。DB 端 CASE WHEN 一次聚合（DATABASE §6.2 observeNetByParty）。
 */
data class NetByPartyRow(
    val partyName: String,
    val net: Long,
)