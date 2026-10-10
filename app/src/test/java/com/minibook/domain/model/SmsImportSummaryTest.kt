package com.minibook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-14 计数闭合不变量单测（设计 G1 / §3.3）。
 *
 * 「不遗漏」的可验证形式：任何口径漂移都必须**立刻失败**，而不是静默少算几条。
 */
class SmsImportSummaryTest {

    @Test
    fun `合法汇总：两条不变量同时成立`() {
        val summary = SmsImportSummary(
            scanTotal = 10,
            parsed = 6,
            imported = 3,
            existing = 1,
            duplicates = 1,
            ambiguous = 1,
            unrecognized = 3,
            notBill = 1,
        )
        assertEquals(10, summary.scanTotal)
        assertEquals(6, summary.parsed)
    }

    @Test
    fun `扫描总数不闭合 → 抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            SmsImportSummary(scanTotal = 9, parsed = 6, imported = 6, unrecognized = 3, notBill = 1)
        }
    }

    @Test
    fun `解析计数不闭合 → 抛异常`() {
        // 第一条闭合成立（5+3+1=9），第二条不成立（imported 4 ≠ parsed 5，漏算 1 条）
        assertThrows(IllegalArgumentException::class.java) {
            SmsImportSummary(
                scanTotal = 9,
                parsed = 5,
                imported = 4,
                existing = 0,
                unrecognized = 3,
                notBill = 1,
            )
        }
    }

    @Test
    fun `空库：全零合法`() {
        val summary = SmsImportSummary()
        assertEquals(0, summary.scanTotal)
    }

    @Test
    fun `预览：全部未识别也闭合`() {
        val preview = SmsScanPreview(
            scanTotal = 2,
            importable = emptyList(),
            existing = emptyList(),
            duplicates = emptyList(),
            ambiguous = emptyList(),
            unrecognized = listOf(
                SmsPendingItem(1L, "95526", 0L, "摘要", "未找到金额"),
                SmsPendingItem(2L, "95526", 0L, "摘要", "未找到日期"),
            ),
            notBillReasons = emptyList(),
        )
        preview.assertClosed()
        assertEquals(0, preview.parsedTotal)
    }

    @Test
    fun `预览：非账单计入闭合`() {
        val preview = SmsScanPreview(
            scanTotal = 3,
            importable = emptyList(),
            existing = emptyList(),
            duplicates = emptyList(),
            ambiguous = emptyList(),
            unrecognized = emptyList(),
            notBillReasons = listOf("验证码类", "营销类", "空短信"),
        )
        preview.assertClosed()
    }

    @Test
    fun `预览：计数漂移 → 断言失败`() {
        val preview = SmsScanPreview(
            scanTotal = 5,
            importable = emptyList(),
            existing = emptyList(),
            duplicates = emptyList(),
            ambiguous = emptyList(),
            unrecognized = emptyList(),
            notBillReasons = emptyList(),
        )
        val error = assertThrows(IllegalArgumentException::class.java) { preview.assertClosed() }
        assertTrue(error.message!!.contains("不闭合"))
    }

    @Test
    fun `渠道抑制口径：仅 BANK_DIRECT 允许自动记账`() {
        assertTrue(SmsChannel.BANK_DIRECT.allowsAutoImport)
        assertTrue(!SmsChannel.ALIPAY.allowsAutoImport)
        assertTrue(!SmsChannel.WECHAT.allowsAutoImport)
        assertTrue(!SmsChannel.OTHER.allowsAutoImport)
    }
}
