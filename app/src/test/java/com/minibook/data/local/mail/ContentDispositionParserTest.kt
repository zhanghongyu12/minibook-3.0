package com.minibook.data.local.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Content-Disposition 解析单测（F-13，纯 JVM，不发网络请求）。
 *
 * 覆盖：微信下载响应**真实实测样本**（RFC5987 percent 编码 UTF-8）、
 * RFC5987 优先于 filename、回退普通 filename（带引号/裸值）、回退 filename 为
 * percent 编码时解码、null/空白输入、无 filename 参数。
 */
class ContentDispositionParserTest {

    @Test
    fun `真实微信样本按RFC5987解码`() {
        val header = "attachment; " +
            "filename=\"%E5%BE%AE...\"; " +
            "filename*=UTF-8''%E5%BE%AE%E4%BF%A1%E6%94%AF%E4%BB%98%E8%B4%A6%E5%8D%95" +
            "%E6%B5%81%E6%B0%B4%E6%96%87%E4%BB%B6(20260619-20260919)%E2%80%94%E2%80%94" +
            "%E3%80%90%E8%A7%A3%E5%8E%8B%E5%AF%86%E7%A0%81%E5%8F%AF%E5%9C%A8%E5%BE%AE%E4%BF%A1" +
            "%E6%94%AF%E4%BB%98%E5%85%AC%E4%BC%97%E5%8F%B7%E6%9F%A5%E7%9C%8B%E3%80%91.zip"
        assertEquals(
            "微信支付账单流水文件(20260619-20260919)——【解压密码可在微信支付公众号查看】.zip",
            ContentDispositionParser.parse(header),
        )
    }

    @Test
    fun `RFC5987优先于filename`() {
        val header = "attachment; filename=\"fallback.zip\"; filename*=UTF-8''%E9%A6%99%E8%95%89.zip"
        assertEquals("香蕉.zip", ContentDispositionParser.parse(header))
    }

    @Test
    fun `回退到普通filename`() {
        assertEquals(
            "bill_20260919.zip",
            ContentDispositionParser.parse("attachment; filename=\"bill_20260919.zip\""),
        )
        assertEquals(
            "bill.zip",
            ContentDispositionParser.parse("attachment; filename=bill.zip"),
        )
    }

    @Test
    fun `回退filename为percent编码UTF-8时解码`() {
        assertEquals(
            "微信账单.zip",
            ContentDispositionParser.parse("attachment; filename=\"%E5%BE%AE%E4%BF%A1%E8%B4%A6%E5%8D%95.zip\""),
        )
    }

    @Test
    fun `null与空白输入返回null`() {
        assertNull(ContentDispositionParser.parse(null))
        assertNull(ContentDispositionParser.parse(""))
        assertNull(ContentDispositionParser.parse("   "))
    }

    @Test
    fun `无filename参数返回null`() {
        assertNull(ContentDispositionParser.parse("attachment"))
        assertNull(ContentDispositionParser.parse("inline; size=1024"))
    }
}
