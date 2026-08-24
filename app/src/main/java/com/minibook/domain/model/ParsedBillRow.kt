package com.minibook.domain.model

/**
 * 账单解析中间模型（F-12，API §13.3 / ARCHITECTURE §16.3）。
 *
 * 纯 Kotlin、金额「分」整数语义（[amountFen]，经 [com.minibook.util.MoneyFormatter.toFen]），
 * 是 [com.minibook.domain.usecase.bill.BillParser] 的统一输出；`platformCategory` 仅在
 * 自动分类时暂存、**不落库**（DEC-019）。
 *
 * 字段语义对齐 PRD §F-12 字段映射表：
 * - [date] `yyyy-MM-dd`（交易时间仅取日期，时分秒丢弃）；
 * - [type] 收/支 → expense/income；
 * - [amountFen] 金额转分（>0）；
 * - [counterparty]/[paymentMethod] 原样映射（空/全空白→null）；
 * - [description] 商品说明 + 备注（备注非空且≠「/」以 `｜` 追加；≤200 截断）；
 * - [externalId] `alipay:` / `wechat:` 前缀包裹原单号；
 * - [platformCategory] 原始平台分类（仅自动分类映射用）。
 */
data class ParsedBillRow(
    val date: String,
    val type: TransactionType,
    val amountFen: Long,
    val counterparty: String?,
    val paymentMethod: String?,
    val description: String,
    val externalId: String,
    val platformCategory: String?,
)