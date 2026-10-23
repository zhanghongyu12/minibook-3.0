package com.minibook.domain.usecase.category

/**
 * 内置商户词典（docs/15 第 7 条，2026-10-13 用户定案：「联网搜索一波大的，然后当做内置的，
 * 学习的就都在 app 本地」）。
 *
 * 形态：**静态词典随 App 打包**（`res/raw/merchant_dictionary.json`，离线可用，升级即更新），
 * 不上传任何数据、不需要账号（零隐私风险）；「学习」完全留在本地（自学习规则仍是第一优先级）。
 *
 * 优先级：**本地学习 > 内置词典 > 内置类型映射 > 其他**；词典命中**只作手动导入预览的建议**
 * （`allowWeakRuleMatch = true` 的路径），L2 到达即记账**不查词典**——防止全局词典把某一笔
 * 静默写错（优先级①「不写错账」）。长尾仍落「其他」+ 本地学习兜底。
 *
 * 实现注记：词典装载（读 JSON）是 I/O，属 data 层；本接口把「确保已装载」与「查询建议」
 * 暴露给 domain 使用方（[com.minibook.domain.usecase.bill.ImportBillUseCase]）。
 */
interface MerchantDictionary {

    /**
     * 确保词典已装载（**幂等**；失败静默返回 —— 词典只是「建议」，绝不应阻断导入主流程）。
     */
    suspend fun ensureLoaded()

    /**
     * 按对手方给出建议的**内置分类名**（如「餐饮」）；无建议返回 null。
     *
     * 匹配口径（见 [BuiltInMerchantDictionary.suggest]）：归一化后相等优先，其次归一化后
     * 包含（取最长关键词 = 更具体的品牌），全部经过与分类规则同一套 [CategoryRuleNormalizer]。
     */
    fun suggest(counterparty: String): String?
}
