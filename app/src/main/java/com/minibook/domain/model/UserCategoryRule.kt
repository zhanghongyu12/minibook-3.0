package com.minibook.domain.model

/**
 * 用户自学习归类规则领域模型（F-12 分类自学习）。
 *
 * 匹配粒度=子串：导入时 `counterparty.contains(keyword)` 命中即用 [categoryId]；
 * 由「导入交易分类被手动改且确实变了」时 upsert 一条（keyword=交易对方全名）。
 *
 * - [source] 与交易同口径（wechat_import / alipay_import）；
 * - [createdAt]/[updatedAt] ISO-8601 时间戳（学习/覆写时刷新，多规则命中按 updatedAt 取最新）。
 */
data class UserCategoryRule(
    val id: String,
    val ledgerId: String,
    val source: TransactionSource,
    val keyword: String,
    val categoryId: String,
    val createdAt: String,
    val updatedAt: String,
)