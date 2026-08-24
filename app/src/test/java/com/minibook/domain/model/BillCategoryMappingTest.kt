package com.minibook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F-12 账单自动分类映射单测（DEC-019，PRD §F-12 自动分类表逐行一致）。
 *
 * 覆盖：支付宝 10 类别映射；微信红包 支出→人情/收入→礼金（含「微信红包（单发）」子串命中）；
 * 未列出组合 / 平台不匹配 / null / 空白 → null（归该 type 的「其他」）。
 */
class BillCategoryMappingTest {

    @Test
    fun `支付宝支出类别映射逐行一致`() {
        val cases = mapOf(
            "餐饮美食" to "餐饮",
            "交通出行" to "交通",
            "日用百货" to "购物",
            "服饰装扮" to "服饰",
            "文化休闲" to "娱乐",
            "运动户外" to "娱乐",
            "酒店旅游" to "旅行",
            "家居家装" to "住房",
            "医疗健康" to "医疗",
            "教育培训" to "教育",
        )
        cases.forEach { (platformCategory, builtIn) ->
            assertEquals(
                builtIn,
                BillCategoryMapping.resolve(TransactionSource.ALIPAY_IMPORT, platformCategory, TransactionType.EXPENSE),
            )
        }
    }

    @Test
    fun `微信红包支出人情 - 收入礼金`() {
        assertEquals("人情", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.EXPENSE))
        assertEquals("礼金", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.INCOME))
        // 子串命中：「微信红包（单发）」含「微信红包」（DEC-019）
        assertEquals("礼金", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包（单发）", TransactionType.INCOME))
    }

    @Test
    fun `未列出组合或平台不匹配返回 null 归其他`() {
        // 未列入上表的组合（PRD §F-12 明列：退款/投资理财/商户消费/零钱提现/转账…）
        assertNull(BillCategoryMapping.resolve(TransactionSource.ALIPAY_IMPORT, "退款", TransactionType.EXPENSE))
        assertNull(BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "商户消费", TransactionType.EXPENSE))
        assertNull(BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "零钱提现", TransactionType.EXPENSE))
        // 平台不匹配：微信红包仅在 WECHAT_IMPORT 命中
        assertNull(BillCategoryMapping.resolve(TransactionSource.ALIPAY_IMPORT, "微信红包", TransactionType.EXPENSE))
        // null / 空白
        assertNull(BillCategoryMapping.resolve(TransactionSource.ALIPAY_IMPORT, null, TransactionType.EXPENSE))
        assertNull(BillCategoryMapping.resolve(TransactionSource.ALIPAY_IMPORT, "   ", TransactionType.EXPENSE))
    }
}