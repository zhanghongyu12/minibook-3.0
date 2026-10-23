package com.minibook.domain.usecase.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「导入来源」清单构造（docs/15 第 2 条，2026-10-13）单测。
 *
 * 只测纯函数 [SmsSenderSummaryBuilder.build]：importEnabled 标记、排序（账单多在前、同数按号码升序）、
 * 机构名透传。聚合与「关掉即不计入」的完整链路依赖 Android 短信库/DataStore，留给模拟器实测。
 */
class SmsSenderSummaryBuilderTest {

    private fun agg(total: Int, bill: Int, institution: String? = null, last: Long = 100L) =
        SmsSenderAggregate().apply {
            totalCount = total
            billCount = bill
            this.institution = institution
            lastAtMs = last
        }

    @Test
    fun `未勾选来源标记 importEnabled=false`() {
        val result = SmsSenderSummaryBuilder.build(
            aggregates = mapOf("95526" to agg(5, 3, "建设银行")),
            noImport = setOf("95526"),
        )
        assertEquals(1, result.size)
        assertFalse("被关掉的来源应标记为不允许导入", result.first().importEnabled)
    }

    @Test
    fun `默认允许导入且机构名透传`() {
        val result = SmsSenderSummaryBuilder.build(
            aggregates = mapOf("10690000" to agg(4, 2, "支付宝")),
            noImport = emptySet(),
        )
        assertEquals("支付宝", result.first().institution)
        assertTrue("默认允许导入", result.first().importEnabled)
    }

    @Test
    fun `排序：账单多的在前 同数按号码升序`() {
        val result = SmsSenderSummaryBuilder.build(
            aggregates = linkedMapOf(
                "95588" to agg(1, 1),
                "95526" to agg(6, 4, "工商银行"),
                "10690000" to agg(6, 4),
            ),
            noImport = emptySet(),
        )
        assertEquals(listOf("95526", "10690000", "95588"), result.map { it.sender })
        assertEquals(listOf(4, 4, 1), result.map { it.billCount })
    }

    @Test
    fun `猜不出机构名则字段为 null`() {
        val result = SmsSenderSummaryBuilder.build(
            aggregates = mapOf("95588" to agg(1, 1)),
            noImport = emptySet(),
        )
        assertEquals(null, result.first().institution)
    }
}
