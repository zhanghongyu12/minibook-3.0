package com.minibook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F-12 账单自动分类映射单测（DEC-019，PRD §F-12 自动分类表逐行一致 + 分类校准/关键词扩展）。
 *
 * 覆盖：支付宝 13 类别映射（含 家居家装→购物、美容美发→购物、商业/生活服务→缴费 校准）；
 * 微信红包 支出→人情/收入→礼金（含「微信红包（单发）」子串命中）、「-退款」变体优先命中「退款」；
 * 微信 转账/群收款/二维码收款 类型映射；对手方关键词（仅微信）命中/未命中/空/非微信。
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
            // F-12 校准：家居家装 由「住房」改「购物」；美容美发 → 「购物」。
            "家居家装" to "购物",
            "美容美发" to "购物",
            "医疗健康" to "医疗",
            "教育培训" to "教育",
            // F-12 校准：商业服务 / 生活服务 → 「缴费」。
            "商业服务" to "缴费",
            "生活服务" to "缴费",
        )
        cases.forEach { (platformCategory, builtIn) ->
            assertEquals(
                builtIn,
                BillCategoryMapping.resolve(TransactionSource.ALIPAY_IMPORT, platformCategory, TransactionType.EXPENSE),
            )
        }
    }

    @Test
    fun `微信红包支出人情 - 收入礼金 - 退款变体优先`() {
        assertEquals("人情", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.EXPENSE))
        assertEquals("礼金", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包", TransactionType.INCOME))
        // 子串命中：「微信红包（单发）」含「微信红包」（DEC-019）
        assertEquals("礼金", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包（单发）", TransactionType.INCOME))
        // 「-退款」变体须在「微信红包」之前命中（否则 firstOrNull 会被「微信红包」先命中误判为礼金）
        assertEquals("退款", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "微信红包-退款", TransactionType.INCOME))
        assertEquals("退款", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "美团平台商户-退款", TransactionType.INCOME))
    }

    @Test
    fun `微信转账收款类型映射`() {
        assertEquals("转账", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "转账", TransactionType.EXPENSE))
        assertEquals("收款", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "转账", TransactionType.INCOME))
        assertEquals("转账", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "群收款", TransactionType.EXPENSE))
        assertEquals("收款", BillCategoryMapping.resolve(TransactionSource.WECHAT_IMPORT, "二维码收款", TransactionType.INCOME))
    }

    @Test
    fun `微信对手方关键词映射仅微信子串命中`() {
        val wechat = TransactionSource.WECHAT_IMPORT
        assertEquals("美团", BillCategoryMapping.resolveByCounterparty(wechat, "美团外卖-某某店", TransactionType.EXPENSE))
        assertEquals("交通", BillCategoryMapping.resolveByCounterparty(wechat, "滴滴出行", TransactionType.EXPENSE))
        assertEquals("购物", BillCategoryMapping.resolveByCounterparty(wechat, "永辉超市", TransactionType.EXPENSE))
        assertEquals("餐饮", BillCategoryMapping.resolveByCounterparty(wechat, "兰州拉面馆", TransactionType.EXPENSE))
        // 未命中 / 空 / 非微信 → null
        assertNull(BillCategoryMapping.resolveByCounterparty(wechat, "不认识的名字", TransactionType.EXPENSE))
        assertNull(BillCategoryMapping.resolveByCounterparty(wechat, null, TransactionType.EXPENSE))
        assertNull(BillCategoryMapping.resolveByCounterparty(TransactionSource.ALIPAY_IMPORT, "美团", TransactionType.EXPENSE))
    }

    @Test
    fun `未列出组合或平台不匹配返回 null 归其他`() {
        // 未列入上表的组合（PRD §F-12 明列：退款/投资理财/商户消费/零钱提现…）
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