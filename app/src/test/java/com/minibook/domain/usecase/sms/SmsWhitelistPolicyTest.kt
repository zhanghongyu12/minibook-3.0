package com.minibook.domain.usecase.sms

import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.SmsChannel
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.SmsWhitelistEntry
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-14 白名单准入策略单测（设计 §3.8：四条件 + 晋级/降级）。 */
class SmsWhitelistPolicyTest {

    private fun parsed(
        channel: SmsChannel = SmsChannel.BANK_DIRECT,
        confidence: Int = 3,
    ): SmsParseOutcome.Parsed = SmsParseOutcome.Parsed(
        row = ParsedBillRow(
            date = "2026-10-08",
            type = TransactionType.EXPENSE,
            amountFen = 10_000L,
            counterparty = null,
            paymentMethod = "网银在线",
            description = "短信消费",
            externalId = "sms:abc",
            platformCategory = null,
        ),
        channel = channel,
        cardTail = "3170",
        balanceFen = 60_591L,
        confidence = confidence,
        sender = "95526",
        receivedAtMs = 1_791_518_400_000L,
        contentHash = "hash",
    )

    private fun entry(enabled: Boolean, count: Int = 3) =
        SmsWhitelistEntry(sender = "95526", autoEnabled = enabled, confirmedCount = count)

    @Test
    fun `四条件全满足 → 自动记账`() {
        val decision = SmsWhitelistPolicy.evaluate(entry(true), parsed(), SmsMatchDecision.New)
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.AutoImport)
    }

    @Test
    fun `条件一不满足：未在白名单 → 待确认`() {
        val decision = SmsWhitelistPolicy.evaluate(null, parsed(), SmsMatchDecision.New)
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.Pending)
    }

    @Test
    fun `条件一不满足：白名单已关闭 → 待确认`() {
        val decision = SmsWhitelistPolicy.evaluate(entry(false), parsed(), SmsMatchDecision.New)
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.Pending)
    }

    @Test
    fun `条件二不满足：置信度不足 → 待确认`() {
        val decision = SmsWhitelistPolicy.evaluate(entry(true), parsed(confidence = 1), SmsMatchDecision.New)
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.Pending)
        assertTrue((decision as SmsWhitelistPolicy.AutoDecision.Pending).reason.contains("置信度"))
    }

    @Test
    fun `条件三不满足：唯一跨源重复 → 待确认`() {
        val tx = Transaction(
            id = "t1",
            ledgerId = "l1",
            type = TransactionType.EXPENSE,
            amount = 10_000L,
            categoryId = "c1",
            date = "2026-10-08",
            createdAt = "2026-10-08T00:00:00",
            updatedAt = "2026-10-08T00:00:00",
            source = TransactionSource.ALIPAY_IMPORT,
        )
        val decision = SmsWhitelistPolicy.evaluate(
            entry(true),
            parsed(),
            SmsMatchDecision.Duplicate(tx, "重复"),
        )
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.Pending)
        assertTrue((decision as SmsWhitelistPolicy.AutoDecision.Pending).reason.contains("重复"))
    }

    @Test
    fun `条件三不满足：多候选 → 待确认`() {
        val decision = SmsWhitelistPolicy.evaluate(
            entry(true),
            parsed(),
            SmsMatchDecision.Ambiguous(emptyList()),
        )
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.Pending)
    }

    @Test
    fun `条件四不满足：支付宝渠道 → 待确认（渠道抑制）`() {
        val decision = SmsWhitelistPolicy.evaluate(
            entry(true),
            parsed(channel = SmsChannel.ALIPAY),
            SmsMatchDecision.New,
        )
        assertTrue(decision is SmsWhitelistPolicy.AutoDecision.Pending)
        assertTrue((decision as SmsWhitelistPolicy.AutoDecision.Pending).reason.contains("账单文件"))
    }

    @Test
    fun `晋级：手动确认三次后自动开启`() {
        var e: SmsWhitelistEntry? = null
        e = SmsWhitelistPolicy.afterManualConfirm(e)
        assertFalse("一次确认不应晋级", e.autoEnabled)
        e = SmsWhitelistPolicy.afterManualConfirm(e)
        assertFalse("两次确认不应晋级", e.autoEnabled)
        e = SmsWhitelistPolicy.afterManualConfirm(e)
        assertTrue("三次确认应晋级", e.autoEnabled)
        assertEquals(3, e.confirmedCount)
    }

    @Test
    fun `晋级后用户主动关闭：再次确认不自动重开`() {
        var e: SmsWhitelistEntry? = entry(enabled = true, count = 5)
        e = SmsWhitelistPolicy.afterManualConfirm(e)
        assertTrue("用户已关闭时不应被自动重开", e!!.autoEnabled)
        var disabled: SmsWhitelistEntry? = entry(enabled = false, count = 5)
        disabled = SmsWhitelistPolicy.afterManualConfirm(disabled)
        assertFalse("已达阈值但用户关闭 → 保持关闭", disabled!!.autoEnabled)
    }

    @Test
    fun `纠错降级：关闭自动且计数清零`() {
        val corrected = SmsWhitelistPolicy.afterUserCorrection(entry(true, count = 9))
        assertFalse(corrected!!.autoEnabled)
        assertEquals(0, corrected.confirmedCount)
    }

    @Test
    fun `纠错降级：无条目时返回 null`() {
        assertEquals(null, SmsWhitelistPolicy.afterUserCorrection(null))
    }

    @Test
    fun `错误留痕：记录时间戳`() {
        val e = SmsWhitelistPolicy.afterError(entry(true), nowMs = 123L)
        assertEquals(123L, e!!.lastErrorAtMs)
    }

    @Test
    fun `晋级阈值常量=3（与设计拍板一致）`() {
        assertEquals(3, SmsWhitelistPolicy.AUTO_PROMOTE_THRESHOLD)
    }
}
