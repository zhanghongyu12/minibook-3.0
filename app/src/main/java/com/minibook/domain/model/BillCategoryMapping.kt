package com.minibook.domain.model

/**
 * F-12 账单自动分类映射常量（DEC-019，PRD §F-12 自动分类表逐行一致）。
 *
 * 键 = `(platform, platformCategory 子串, type)`，值 = 目标内置分类名。映射产物仅为
 * 「目标内置分类名」，由 ImportBillUseCase 在快照分类里按 `(type, name)` 精确匹配为
 * categoryId，查不到回退该 type 的「其他」内置分类（见 §16.7）。原始 `platformCategory`
 * 只在解析中间模型暂存、**不落库**。
 *
 * 未列入上表的任何「平台分类 × 收/支」组合（含支付宝「退款/信用借还/投资理财/…/其他」、
 * 微信「商户消费/扫二维码付款/转账/…/零钱提现/…」等）一律返回 null → 归该 type 的「其他」。
 *
 * 匹配口径（DEC-019）：`platformCategory 子串` 命中——即账单原始平台分类**包含**表内键时
 * 判定命中（兼容「微信红包（单发）」等带尾缀变体）。
 */
object BillCategoryMapping {

    /** 「其他」兜底分类固定名（DEC-007 锁定默认名，恒可定位作兜底锚点）。 */
    const val OTHERS_CATEGORY_NAME: String = "其他"

    private data class Entry(
        val platform: TransactionSource,
        val platformCategory: String,
        val type: TransactionType,
        val builtInCategory: String,
    )

    /** 映射表（PRD §F-12 表逐行）；列表顺序即匹配优先级（键互不重叠，顺序无实际影响）。 */
    private val ENTRIES: List<Entry> = listOf(
        // 支付宝 → 支出
        Entry(TransactionSource.ALIPAY_IMPORT, "餐饮美食", TransactionType.EXPENSE, "餐饮"),
        Entry(TransactionSource.ALIPAY_IMPORT, "交通出行", TransactionType.EXPENSE, "交通"),
        Entry(TransactionSource.ALIPAY_IMPORT, "日用百货", TransactionType.EXPENSE, "购物"),
        Entry(TransactionSource.ALIPAY_IMPORT, "服饰装扮", TransactionType.EXPENSE, "服饰"),
        Entry(TransactionSource.ALIPAY_IMPORT, "文化休闲", TransactionType.EXPENSE, "娱乐"),
        Entry(TransactionSource.ALIPAY_IMPORT, "运动户外", TransactionType.EXPENSE, "娱乐"),
        Entry(TransactionSource.ALIPAY_IMPORT, "酒店旅游", TransactionType.EXPENSE, "旅行"),
        Entry(TransactionSource.ALIPAY_IMPORT, "家居家装", TransactionType.EXPENSE, "住房"),
        Entry(TransactionSource.ALIPAY_IMPORT, "医疗健康", TransactionType.EXPENSE, "医疗"),
        Entry(TransactionSource.ALIPAY_IMPORT, "教育培训", TransactionType.EXPENSE, "教育"),
        // 微信红包 → 支出人情 / 收入礼金
        Entry(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.EXPENSE, "人情"),
        Entry(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.INCOME, "礼金"),
    )

    /**
     * 映射平台分类 → 目标内置分类名。
     *
     * @return 命中的内置分类名；未列出组合 / platformCategory 为 null/空 → null（归「其他」兜底）。
     */
    fun resolve(
        platform: TransactionSource,
        platformCategory: String?,
        type: TransactionType,
    ): String? {
        val raw = platformCategory?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return ENTRIES.firstOrNull {
            it.platform == platform && it.type == type && raw.contains(it.platformCategory)
        }?.builtInCategory
    }
}