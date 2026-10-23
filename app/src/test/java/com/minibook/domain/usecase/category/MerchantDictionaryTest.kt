package com.minibook.domain.usecase.category

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置商户词典（docs/15 第 7 条 / docs/20）——资源文件守门 + 匹配器行为。
 *
 * 为什么要在单测里读资源文件：词典是**静态资源**，词条写错（分类名拼错、关键词重复、夹带不该有的内容）
 * 编译期完全看不出来，只有在真机上才会表现为「建议了不存在的分类」或「静默归错」。这里做三道守门：
 * 1. 每条都能映射到一个**合法内置分类名**；
 * 2. 关键词**唯一**；
 * 3. 不夹带明显的个人信息（手机号/身份证号样式）。
 */
class MerchantDictionaryTest {

    private val allowedCategories: Set<String> = setOf(
        // 支出内置分类（BuiltInCategories 模板名）
        "餐饮", "交通", "购物", "娱乐", "住房", "医疗", "教育", "服饰", "通讯", "人情",
        "旅行", "美团", "转账", "缴费", "付款",
        // 收入内置分类
        "工资", "奖金", "投资", "兼职", "礼金", "收款", "退款",
        // 兜底
        "其他",
    )

    private fun dictionaryFile(): File {
        val candidates = listOf(
            File("src/main/res/raw/merchant_dictionary.json"),
            File("app/src/main/res/raw/merchant_dictionary.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到词典资源，候选路径：" + candidates.joinToString { it.absolutePath })
    }

    /** 极简解析：资源是我们自己生成的紧凑 JSON，条目形如 {"keyword":"…","category":"…"}。 */
    private fun entries(): List<Pair<String, String>> {
        val text = dictionaryFile().readText()
        val regex = Regex("\"keyword\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"category\"\\s*:\\s*\"([^\"]*)\"")
        return regex.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    @Test
    fun `词典资源 - 条目规模与分类合法 关键词唯一`() {
        val entries = entries()
        assertTrue("词典条目过少：${entries.size}（docs/20 目标是 1000+）", entries.size >= 1000)

        val badCategories = entries.filter { it.second !in allowedCategories }
        assertTrue("存在非法分类名：$badCategories", badCategories.isEmpty())

        val duplicates = entries.groupBy { it.first }.filterValues { it.size > 1 }.keys
        assertTrue("存在重复关键词：$duplicates", duplicates.isEmpty())

        val suspicious = entries.filter { (keyword, _) ->
            Regex("^1[3-9]\\d{9}$").matches(keyword) || Regex("\\d{15,18}").containsMatchIn(keyword)
        }
        assertTrue("疑似夹带个人信息：$suspicious", suspicious.isEmpty())
    }

    @Test
    fun `匹配器 - 归一化相等与包含 取更长关键词 未覆盖返回 null`() {
        BuiltInMerchantDictionary.install(
            listOf(
                BuiltInMerchantDictionary.Entry("罗森", "购物"),
                BuiltInMerchantDictionary.Entry("星巴克", "餐饮"),
                BuiltInMerchantDictionary.Entry("星巴克臻选", "娱乐"),
            ),
        )
        try {
            // 归一化后相等：「罗森(北京)有限公司房山店」→「罗森房山店」不相等，但包含「罗森」；
            // 这里先验「门店名包含品牌」路径。
            assertEquals("购物", BuiltInMerchantDictionary.suggest("罗森(北京)有限公司房山店"))
            // 关键词按长度降序：「星巴克臻选」更长 ⇒ 先命中，说明「更具体优先」生效。
            assertEquals("娱乐", BuiltInMerchantDictionary.suggest("星巴克臻选(上海)有限公司"))
            // 归一化后完全相等（整串就是品牌名）。
            assertEquals("餐饮", BuiltInMerchantDictionary.suggest("星巴克"))
            // 未覆盖 → null（绝不硬猜）。
            assertNull(BuiltInMerchantDictionary.suggest("街角无名小店"))
            assertNull(BuiltInMerchantDictionary.suggest(""))
        } finally {
            BuiltInMerchantDictionary.clear()
        }
    }

    @Test
    fun `匹配器 - 未装载时不建议且不崩`() {
        BuiltInMerchantDictionary.clear()
        assertNull(BuiltInMerchantDictionary.suggest("罗森(北京)有限公司房山店"))
        assertEquals(0, BuiltInMerchantDictionary.size)
    }
}
