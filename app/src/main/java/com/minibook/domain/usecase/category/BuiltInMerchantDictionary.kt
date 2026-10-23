package com.minibook.domain.usecase.category

/**
 * 内置商户词典的**进程内注册表 + 匹配器**（docs/15 第 7 条，2026-10-13）。
 *
 * 数据由 data 层 [com.minibook.data.local.dictionary.MerchantDictionaryStore] 装载进
 * [install]（`res/raw/merchant_dictionary.json`）；本对象纯 Kotlin、无 Android 依赖 → JVM 可测。
 *
 * 匹配口径（与自学习规则共用同一套 [CategoryRuleNormalizer]）：
 * 1. **归一化后相等** → 命中（如对手方「罗森(北京)有限公司房山店」归一化「罗森房山店」== 词典词）;
 * 2. **归一化后包含** → 命中，取**关键词最长**者（更具体的品牌优先，如「星巴克臻选」先于「星巴克」）。
 *
 * [install] 线程安全（@Volatile 双缓冲）：扫描/导入期间装载完成即切换，不打断进行中的导入。
 */
object BuiltInMerchantDictionary {

    /** 词典条目（keyword 为原始词；匹配前统一过 [CategoryRuleNormalizer]）。 */
    data class Entry(val keyword: String, val category: String)

    /** 已装载条目；按归一化关键词长度降序（最长的先试，更具体）。 */
    @Volatile
    private var installed: List<Entry> = emptyList()

    /** 当前条数（测试与报告用）。 */
    val size: Int get() = installed.size

    /** 装载（幂等由调用方保证；重复 install 以最新为准）。 */
    fun install(entries: List<Entry>) {
        installed = entries.sortedByDescending { CategoryRuleNormalizer.normalize(it.keyword).length }
    }

    /** 清空（测试用）。 */
    fun clear() {
        installed = emptyList()
    }

    /**
     * @return 建议的内置分类名（如「餐饮」）；无建议返回 null。
     */
    fun suggest(counterparty: String): String? {
        val raw = counterparty.trim()
        if (raw.isEmpty() || installed.isEmpty()) return null
        val normalized = CategoryRuleNormalizer.normalize(raw)
        if (normalized.isEmpty()) return null
        // 1) 归一化后完全相等（最稳：整串就是品牌名）。
        installed.firstOrNull { CategoryRuleNormalizer.normalize(it.keyword) == normalized }?.let { return it.category }
        // 2) 归一化后包含（关键词已按长度降序 ⇒ 最具体的品牌先命中）。
        installed.firstOrNull { normalized.contains(CategoryRuleNormalizer.normalize(it.keyword)) }?.let { return it.category }
        return null
    }
}
