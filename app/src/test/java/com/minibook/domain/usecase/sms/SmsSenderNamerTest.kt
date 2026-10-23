package com.minibook.domain.usecase.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「导入来源」机构名猜测（docs/15 第 2 条，2026-10-13）单测。
 *
 * 只测纯函数 [SmsSenderNamer.name]：猜出机构名、长名优先、猜不出返回 null（UI 回退显示号码）。
 */
class SmsSenderNamerTest {

    @Test
    fun `正文含机构名：建设银行`() {
        assertEquals(
            "建设银行",
            SmsSenderNamer.name("10690000", "【建设银行】尊敬的客户，我行可特批您398000元周转款…"),
        )
    }

    @Test
    fun `长名优先于短名`() {
        assertEquals(
            "中国邮政储蓄银行",
            SmsSenderNamer.name("95580", "【中国邮政储蓄银行】您的账户余额变动提醒…"),
        )
    }

    @Test
    fun `正文无机构名但带签名：用签名`() {
        assertEquals("农商银行", SmsSenderNamer.name("1069", "【农商银行】您好，我行授予您398000元…"))
    }

    @Test
    fun `方括号签名也认`() {
        assertEquals("北京银行", SmsSenderNamer.name("1069", "[北京银行] 您尾号1234账户 8 月账单已出…"))
    }

    @Test
    fun `猜不出返回 null 不硬写`() {
        assertNull(SmsSenderNamer.name("95588", "您尾号1234的账户消费88.00元，余额520.00元"))
    }

    @Test
    fun `空正文返回 null`() {
        assertNull(SmsSenderNamer.name("10690000", ""))
    }
}
