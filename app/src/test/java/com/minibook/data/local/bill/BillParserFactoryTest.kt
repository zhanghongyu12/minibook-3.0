package com.minibook.data.local.bill

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 账单解析器工厂判型单测（TASK-041/042，DEC-017，ARCHITECTURE §16.3，PRD §F-12 异常处理）。
 *
 * 覆盖「内容嗅探主判 + 扩展名分发 + 未识别」三径：`.csv`→支付宝、`.xlsx`→微信（大小写不敏感）、
 * `PK\x03\x04` zip 魔数兜底→微信（含 .csv 名但内容 zip 的失配场景）、未知扩展名且非 zip→null
 * （「未识别账单格式」零落库）、无扩展名且非 zip→按文本 CSV 试解。此判型路径 Original 无单测，
 * 补齐 DEC-017「格式探测」验收分支。
 */
class BillParserFactoryTest {

    @Test
    fun `扩展名 csv 命中支付宝解析器`() {
        assertSame(AlipayCsvParser, DefaultBillParserFactory.resolve("支付宝交易明细.csv", null))
    }

    @Test
    fun `扩展名 xlsx 命中微信解析器且大小写不敏感`() {
        assertSame(WechatXlsxParser, DefaultBillParserFactory.resolve("微信账单.XLSX", null))
        assertSame(WechatXlsxParser, DefaultBillParserFactory.resolve("微信账单.xlsx", null))
    }

    @Test
    fun `zip 魔数兜底命中微信解析器即使扩展名非 xlsx`() {
        val magic = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)
        assertSame(WechatXlsxParser, DefaultBillParserFactory.resolve("伪装账单.bin", magic))
    }

    @Test
    fun `csv 扩展名但 zip 魔数内容嗅探优先命中微信解析器`() {
        // P2-1 回归：.csv 名但内容为 zip → 魔数前置，不再被扩展名误分发到支付宝 CSV。
        val magic = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)
        assertSame(WechatXlsxParser, DefaultBillParserFactory.resolve("改名账单.csv", magic))
    }

    @Test
    fun `未知扩展名且非 zip 魔数返回 null 未识别`() {
        assertNull(DefaultBillParserFactory.resolve("账单.txt", byteArrayOf(0x31, 0x32, 0x33, 0x34)))
    }

    @Test
    fun `无扩展名且非 zip 魔数按文本 CSV 试解`() {
        assertSame(AlipayCsvParser, DefaultBillParserFactory.resolve("无扩展名文件", byteArrayOf(0x31, 0x32, 0x33)))
    }

    @Test
    fun `displayName 为 null 且无 zip 魔数按 CSV 试解`() {
        assertSame(AlipayCsvParser, DefaultBillParserFactory.resolve(null, null))
    }

    @Test
    fun `不足 4 字节的短魔数不算 zip`() {
        val short = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
        assertNull(DefaultBillParserFactory.resolve("账单.dat", short))
    }
}