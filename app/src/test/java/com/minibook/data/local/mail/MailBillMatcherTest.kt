package com.minibook.data.local.mail

import com.minibook.domain.model.BillPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 账单邮件本地过滤器单测（F-13，纯 JVM）。
 *
 * 覆盖：支付宝/微信命中、都不命中、发件人带显示名（`张三 <...>`）、发件人与主题大小写
 * 差异、主题关键词缺失（发件人命中但主题不中）、null 输入、空串输入，以及「发件人命中
 * 支付宝但主题是微信关键词」的交叉不命中（支付宝优先判定要求两者同时满足）。
 */
class MailBillMatcherTest {

    private val matcher = MailBillMatcher()

    @Test
    fun `支付宝发件人与主题关键词同时命中`() {
        assertEquals(
            BillPlatform.ALIPAY,
            matcher.platformOf("service@mail.alipay.com", "您的支付宝交易流水明细"),
        )
    }

    @Test
    fun `微信发件人与主题关键词同时命中`() {
        assertEquals(
            BillPlatform.WECHAT,
            matcher.platformOf("wechatpay@tencent.com", "微信支付-账单流水文件"),
        )
    }

    @Test
    fun `普通邮件发件人与主题都不命中返回null`() {
        assertNull(matcher.platformOf("friend@example.com", "周末爬山安排"))
    }

    @Test
    fun `发件人带显示名仍命中`() {
        assertEquals(
            BillPlatform.ALIPAY,
            matcher.platformOf("张三 <service@mail.alipay.com>", "支付宝交易流水明细"),
        )
        assertEquals(
            BillPlatform.WECHAT,
            matcher.platformOf("微信支付 <wechatpay@tencent.com>", "微信支付-账单流水文件"),
        )
    }

    @Test
    fun `发件人域大小写不敏感命中`() {
        assertEquals(
            BillPlatform.ALIPAY,
            matcher.platformOf("SERVICE@MAIL.ALIPAY.COM", "支付宝交易流水明细"),
        )
        assertEquals(
            BillPlatform.WECHAT,
            matcher.platformOf("WeChatPay@Tencent.com", "微信支付-账单流水文件"),
        )
    }

    @Test
    fun `发件人命中但主题关键词缺失返回null`() {
        assertNull(matcher.platformOf("service@mail.alipay.com", "系统通知"))
        assertNull(matcher.platformOf("wechatpay@tencent.com", "支付成功通知"))
    }

    @Test
    fun `发件人命中支付宝但主题是微信关键词交叉不命中`() {
        assertNull(matcher.platformOf("service@mail.alipay.com", "微信支付-账单流水文件"))
    }

    @Test
    fun `null输入返回null`() {
        assertNull(matcher.platformOf(null, null))
        assertNull(matcher.platformOf(null, "支付宝交易流水明细"))
        assertNull(matcher.platformOf("service@mail.alipay.com", null))
    }

    @Test
    fun `空串输入返回null`() {
        assertNull(matcher.platformOf("", "支付宝交易流水明细"))
        assertNull(matcher.platformOf("service@mail.alipay.com", ""))
        assertNull(matcher.platformOf("", ""))
    }
}
