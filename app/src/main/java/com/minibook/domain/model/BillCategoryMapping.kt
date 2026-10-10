package com.minibook.domain.model

/**
 * F-12 账单自动分类映射常量（DEC-019，PRD §F-12 自动分类表逐行一致，含 F-12 分类校准扩展）。
 *
 * 键 = `(platform, platformCategory 子串, type)`，值 = 目标内置分类名。映射产物仅为
 * 「目标内置分类名」，由 ImportBillUseCase 在快照分类里按 `(type, name)` 精确匹配为
 * categoryId，查不到回退该 type 的「其他」内置分类（见 §16.7）。原始 `platformCategory`
 * 只在解析中间模型暂存、**不落库**。
 *
 * 未列入上表的任何「平台分类 × 收/支」组合（含支付宝「退款/信用借还/投资理财/…/其他」、
 * 微信「商户消费/扫二维码付款/零钱提现/…」等）一律返回 null → 归该 type 的「其他」（或
 * 微信「扫二维码付款」→「付款」，见 ImportBillUseCase.resolveCategoryId 第 4 步）。
 *
 * 匹配口径（DEC-019）：`platformCategory 子串` 命中——即账单原始平台分类**包含**表内键时
 * 判定命中（兼容「微信红包（单发）」等带尾缀变体）。「-退款」类条目（微信红包-退款 /
 * 美团平台商户-退款）必须用更精确子串（「红包-退款」「平台商户-退款」）且**排在「微信红包」
 * 之前**，否则 [ENTRIES].firstOrNull 会先命中「微信红包」误判为礼金。
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

    /** 映射表（PRD §F-12 表 + F-12 分类校准新条目）；列表顺序即匹配优先级（含子串命中）。 */
    private val ENTRIES: List<Entry> = listOf(
        // 支付宝 → 支出
        Entry(TransactionSource.ALIPAY_IMPORT, "餐饮美食", TransactionType.EXPENSE, "餐饮"),
        Entry(TransactionSource.ALIPAY_IMPORT, "交通出行", TransactionType.EXPENSE, "交通"),
        Entry(TransactionSource.ALIPAY_IMPORT, "日用百货", TransactionType.EXPENSE, "购物"),
        Entry(TransactionSource.ALIPAY_IMPORT, "服饰装扮", TransactionType.EXPENSE, "服饰"),
        Entry(TransactionSource.ALIPAY_IMPORT, "文化休闲", TransactionType.EXPENSE, "娱乐"),
        Entry(TransactionSource.ALIPAY_IMPORT, "运动户外", TransactionType.EXPENSE, "娱乐"),
        Entry(TransactionSource.ALIPAY_IMPORT, "酒店旅游", TransactionType.EXPENSE, "旅行"),
        // F-12 校准：家居家装 由「住房」改「购物」；美容美发 → 购物。
        Entry(TransactionSource.ALIPAY_IMPORT, "家居家装", TransactionType.EXPENSE, "购物"),
        Entry(TransactionSource.ALIPAY_IMPORT, "美容美发", TransactionType.EXPENSE, "购物"),
        Entry(TransactionSource.ALIPAY_IMPORT, "医疗健康", TransactionType.EXPENSE, "医疗"),
        Entry(TransactionSource.ALIPAY_IMPORT, "教育培训", TransactionType.EXPENSE, "教育"),
        // F-12 校准：商业服务 / 生活服务 → 缴费。
        Entry(TransactionSource.ALIPAY_IMPORT, "商业服务", TransactionType.EXPENSE, "缴费"),
        Entry(TransactionSource.ALIPAY_IMPORT, "生活服务", TransactionType.EXPENSE, "缴费"),
        // 微信「-退款」变体：用更精确子串且排在「微信红包」之前（否则「微信红包-退款」被「微信红包」先命中）。
        Entry(TransactionSource.WECHAT_IMPORT, "红包-退款", TransactionType.INCOME, "退款"),
        Entry(TransactionSource.WECHAT_IMPORT, "平台商户-退款", TransactionType.INCOME, "退款"),
        // 微信红包 → 支出人情 / 收入礼金（保留红包→人情/礼金）。
        Entry(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.EXPENSE, "人情"),
        Entry(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.INCOME, "礼金"),
        // 微信转账 / 收款（F-12 校准新增）。
        Entry(TransactionSource.WECHAT_IMPORT, "转账", TransactionType.EXPENSE, "转账"),
        Entry(TransactionSource.WECHAT_IMPORT, "转账", TransactionType.INCOME, "收款"),
        Entry(TransactionSource.WECHAT_IMPORT, "群收款", TransactionType.EXPENSE, "转账"),
        Entry(TransactionSource.WECHAT_IMPORT, "二维码收款", TransactionType.INCOME, "收款"),
    )

    /**
     * 微信对手方（交易对方）关键词 → 目标内置分类名（F-12 校准扩展，仅子串命中）。
     * 列表序即匹配优先级：先命中先返回（表项覆盖口径见实现）。
     */
    private data class KeywordEntry(
        val keywords: List<String>,
        val builtInCategory: String,
    )

    /** 对手方关键词表（B3 逐条）；顺序即优先级，无 type 门（目标分类均为支出侧，见 UseCase 兜底）。 */
    private val COUNTERPARTY_KEYWORDS: List<KeywordEntry> = listOf(
        KeywordEntry(listOf("美团"), "美团"),
        KeywordEntry(listOf("花小猪", "滴滴", "甜冻梨", "出租", "出行"), "交通"),
        KeywordEntry(listOf("超市", "仓买", "食材", "干调", "商场"), "购物"),
        KeywordEntry(listOf("生活缴费", "缴费"), "缴费"),
        KeywordEntry(listOf("采耳", "渔具", "bar", "按摩", "洗浴"), "娱乐"),
        KeywordEntry(listOf("文教"), "教育"),
        KeywordEntry(
            listOf(
                "面馆", "烧烤", "麻辣", "火锅", "肉蟹煲", "刀削", "蛋堡", "炸丸子",
                "饭", "餐厅", "串", "食", "馒头", "早点", "外卖",
            ),
            "餐饮",
        ),
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

    /**
     * 映射交易对方 → 目标内置分类名（微信 + **F-14 短信**、仅对手方子串命中）。
     *
     * 短信通道（F-14）只有「商户/户名」这一条分类线索（没有平台分类），因此复用同一张关键词表：
     * 关键词映射出的均为支出侧分类名（美团/交通/购物/缴费/娱乐/教育/餐饮），落到收入类型时
     * 由 ImportBillUseCase 的 `(type, name)` 精确匹配自然回退「其他」。
     *
     * @return 命中的关键词所映射的内置分类名；非微信/短信 / counterparty null/空 / 未命中 → null。
     */
    fun resolveByCounterparty(
        source: TransactionSource,
        counterparty: String?,
        type: TransactionType,
    ): String? {
        if (source != TransactionSource.WECHAT_IMPORT && source != TransactionSource.SMS_IMPORT) return null
        val raw = counterparty?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return COUNTERPARTY_KEYWORDS
            .firstOrNull { entry -> entry.keywords.any { raw.contains(it) } }
            ?.builtInCategory
    }
}