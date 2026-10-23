package com.minibook.data.local.dictionary

import com.minibook.domain.usecase.category.BuiltInMerchantDictionary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 内置商户词典 JSON 解析（docs/15 第 7 条；纯 Kotlin、**无 Android 依赖** → JVM 可单测）。
 *
 * 文件格式（`app/src/main/res/raw/merchant_dictionary.json`）：
 * ```json
 * { "version": 1, "note": "…", "entries": [ { "keyword": "肯德基", "category": "餐饮" } ] }
 * ```
 * 容错口径：**解析失败或字段缺失一律忽略该条**（词典只是「建议」，绝不能因为一条脏数据让导入挂掉）；
 * 未知字段忽略（`ignoreUnknownKeys`），为将来加字段留余地。
 */
internal object MerchantDictionaryJson {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileDoc(
        val version: Int = 1,
        val entries: List<RawEntry> = emptyList(),
    )

    @Serializable
    private data class RawEntry(
        val keyword: String = "",
        val category: String = "",
    )

    /**
     * @return 解析出的条目（跳过 keyword/category 为空的脏行）；整体解析失败返回空列表。
     */
    fun parse(text: String): List<BuiltInMerchantDictionary.Entry> = runCatching {
        json.decodeFromString<FileDoc>(text).entries
            .map { BuiltInMerchantDictionary.Entry(it.keyword.trim(), it.category.trim()) }
            .filter { it.keyword.isNotEmpty() && it.category.isNotEmpty() }
    }.getOrDefault(emptyList())
}
