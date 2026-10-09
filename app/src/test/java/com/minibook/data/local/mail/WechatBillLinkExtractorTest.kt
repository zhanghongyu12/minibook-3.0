package com.minibook.data.local.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 微信账单下载链接提取器单测（F-13，纯 JVM，不发网络请求）。
 *
 * 覆盖：真实正文片段（含 imgcache.qq.com / wx.gtimg.com 图片与帮助页干扰链接，
 * 必须按 host + 路径挑中 tenpay 那条，且干扰链接在前也不误选）、`&amp;` 反转义、
 * 以引号/中文结尾的正确截断、无账单链接、空串/空白、tenpay 域但路径不符、仿冒域名。
 */
class WechatBillLinkExtractorTest {

    private val extractor = WechatBillLinkExtractor()

    @Test
    fun `正文含干扰链接仍只挑中tenpay账单下载链接`() {
        val body = """
            【微信支付】您申请的账单流水文件已生成，请点击链接下载查看。
            http://imgcache.qq.com/online/collect/customer_service.html
            https://wx.gtimg.com/email/images/wx_pay_logo.png
            点击下载 https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?d=202609191234&bill_type=wechat&sign=AbCdEf1234567890%3D&lang=zh_CN 链接有效期 7 天，请尽快下载。
        """.trimIndent()
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail" +
                "?d=202609191234&bill_type=wechat&sign=AbCdEf1234567890%3D&lang=zh_CN",
            extractor.extract(body),
        )
    }

    @Test
    fun `账单链接出现在干扰链接之后也能命中`() {
        val body = "先看帮助 http://imgcache.qq.com/online/collect/customer_service.html 再" +
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1 下载"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `html形态链接以双引号截断且amp实体反转义`() {
        val body = "<a href=\"https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?d=1&amp;sign=abc\">点击下载</a>"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?d=1&sign=abc",
            extractor.extract(body),
        )
    }

    @Test
    fun `链接以单引号结尾正确截断`() {
        val body = "download: 'https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1'"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `链接后紧跟中文正确截断`() {
        val body = "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1请尽快下载"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `链接以尖括号包裹正确截断`() {
        val body = "下载链接 <https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1> 已生成"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `链接后紧跟反斜杠正确截断`() {
        // JSON/HTML 残留的反斜杠不得被吞进 URL。
        val body = "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1\\n残留"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `链接后紧跟右花括号正确截断`() {
        val body = "{\"url\":\"https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1\"}"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `URL含方括号仍可完整提取`() {
        // 候选正则已改为负字符类：URL 自身含 `[` `]` 不再需要转义标点，天然支持。
        val body = "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?d[0]=1&x[]=2 下载"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?d[0]=1&x[]=2",
            extractor.extract(body),
        )
    }

    @Test
    fun `仅含干扰链接返回null`() {
        assertNull(
            extractor.extract(
                "帮助 http://imgcache.qq.com/online/collect/customer_service.html " +
                    "logo https://wx.gtimg.com/email/images/wx_pay_logo.png",
            ),
        )
    }

    @Test
    fun `含方括号的干扰链接仍不被选中`() {
        assertNull(
            extractor.extract(
                "帮助页 http://imgcache.qq.com/online/[collect]/customer_service.html " +
                    "https://wx.gtimg.com/email/[images]/wx_pay_logo.png",
            ),
        )
    }

    @Test
    fun `空串与空白返回null`() {
        assertNull(extractor.extract(""))
        assertNull(extractor.extract("   \n "))
    }

    @Test
    fun `tenpay域但路径不符返回null`() {
        assertNull(extractor.extract("https://tenpay.wechatpay.cn/other/path?x=1"))
    }

    @Test
    fun `仿冒域名不匹配`() {
        assertNull(
            extractor.extract(
                "https://tenpay.wechatpay.cn.evil.com/userroll/userbilldownload/downloadfilefromemail?x=1",
            ),
        )
    }

    // ------------------------------------------------------------------ 手写扫描新增边界

    @Test
    fun `大写HTTP scheme与全大写host仍命中`() {
        // scheme/host/路径均大小写不敏感；命中后按原文返回（不转小写）。
        val body = "HTTP://TENPAY.WECHATPAY.CN/USERROLL/USERBILLDOWNLOAD/DOWNLOADFILEFROMEMAIL?X=1"
        assertEquals(
            "HTTP://TENPAY.WECHATPAY.CN/USERROLL/USERBILLDOWNLOAD/DOWNLOADFILEFROMEMAIL?X=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `带443端口仍命中且保留端口`() {
        val body = "https://tenpay.wechatpay.cn:443/userroll/userbilldownload/downloadfilefromemail?x=1"
        assertEquals(
            "https://tenpay.wechatpay.cn:443/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `端口后非斜杠不命中`() {
        // `:443userroll`——端口后必须紧跟 `/`，否则不视为账单链接。
        assertNull(
            extractor.extract(
                "https://tenpay.wechatpay.cn:443userroll/userbilldownload/downloadfilefromemail?x=1",
            ),
        )
    }

    @Test
    fun `URL后紧跟反引号正确截断`() {
        val body = "链接 `https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1` 结束"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `URL后紧跟左花括号正确截断`() {
        val body = "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1{残留"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?x=1",
            extractor.extract(body),
        )
    }

    @Test
    fun `多个amp实体全部还原`() {
        val body = "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?a=1&amp;b=2&amp;c=3"
        assertEquals(
            "https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?a=1&b=2&c=3",
            extractor.extract(body),
        )
    }
}
