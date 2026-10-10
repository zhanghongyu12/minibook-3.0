package com.minibook.domain.usecase.sms

import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-14 跨源匹配规则单测（设计 §3.6）。 */
class SmsMatchRulesTest {

    private fun row(date: String = "2026-10-08", fen: Long = 10_000L, type: TransactionType = TransactionType.EXPENSE) =
        ParsedBillRow(
            date = date,
            type = type,
            amountFen = fen,
            counterparty = null,
            paymentMethod = null,
            description = "短信消费",
            externalId = "sms:abc",
            platformCategory = null,
        )

    private fun tx(
        id: String,
        source: TransactionSource,
        date: String = "2026-10-08",
        amount: Long = 10_000L,
    ) = Transaction(
        id = id,
        ledgerId = "ledger-1",
        type = TransactionType.EXPENSE,
        amount = amount,
        categoryId = "cat-1",
        date = date,
        createdAt = "2026-10-08T00:00:00",
        updatedAt = "2026-10-08T00:00:00",
        source = source,
    )

    @Test
    fun `无候选 → 新记录`() {
        val decision = SmsMatchRules.decide(row(), TransactionSource.SMS_IMPORT, emptyList())
        assertTrue(decision is SmsMatchDecision.New)
    }

    @Test
    fun `唯一跨源候选 → 判重并给出依据`() {
        val decision = SmsMatchRules.decide(
            row(),
            TransactionSource.SMS_IMPORT,
            listOf(tx("t1", TransactionSource.ALIPAY_IMPORT)),
        )
        assertTrue(decision is SmsMatchDecision.Duplicate)
        val dup = decision as SmsMatchDecision.Duplicate
        assertEquals("t1", dup.matched.id)
        assertTrue("依据应含金额", dup.reason.contains("100.00"))
        assertTrue("依据应含来源", dup.reason.contains("支付宝"))
    }

    @Test
    fun `两条跨源候选 → 疑似重复（人工确认）`() {
        val decision = SmsMatchRules.decide(
            row(),
            TransactionSource.SMS_IMPORT,
            listOf(
                tx("t1", TransactionSource.ALIPAY_IMPORT),
                tx("t2", TransactionSource.MANUAL),
            ),
        )
        assertTrue(decision is SmsMatchDecision.Ambiguous)
        assertEquals(2, (decision as SmsMatchDecision.Ambiguous).candidates.size)
    }

    @Test
    fun `同来源候选不参与匹配（同源由 externalId 幂等负责）`() {
        val decision = SmsMatchRules.decide(
            row(),
            TransactionSource.SMS_IMPORT,
            listOf(tx("t1", TransactionSource.SMS_IMPORT)),
        )
        assertTrue("同来源不应判为跨源重复，实际=$decision", decision is SmsMatchDecision.New)
    }

    @Test
    fun `账单文件导入方向：反查短信行同样判重（双向）`() {
        val decision = SmsMatchRules.decide(
            row(),
            TransactionSource.ALIPAY_IMPORT,
            listOf(tx("t1", TransactionSource.SMS_IMPORT)),
        )
        assertTrue(decision is SmsMatchDecision.Duplicate)
        assertEquals(TransactionSource.SMS_IMPORT, (decision as SmsMatchDecision.Duplicate).matched.source)
    }

    @Test
    fun `金额格式化：分转元两位`() {
        assertEquals("0.01", SmsMatchRules.formatFen(1))
        assertEquals("100.00", SmsMatchRules.formatFen(10_000))
        assertEquals("1234.56", SmsMatchRules.formatFen(123_456))
    }

    @Test
    fun `来源文案：四种来源可读`() {
        assertEquals("手动记账", SmsMatchRules.sourceLabel(TransactionSource.MANUAL))
        assertEquals("微信账单导入", SmsMatchRules.sourceLabel(TransactionSource.WECHAT_IMPORT))
        assertEquals("支付宝账单导入", SmsMatchRules.sourceLabel(TransactionSource.ALIPAY_IMPORT))
        assertEquals("短信导入", SmsMatchRules.sourceLabel(TransactionSource.SMS_IMPORT))
    }
}
