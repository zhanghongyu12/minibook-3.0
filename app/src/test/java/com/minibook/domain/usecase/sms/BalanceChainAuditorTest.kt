package com.minibook.domain.usecase.sms

import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.SmsChannel
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.TransactionType
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-14 余额链审计单测（设计 §3.10；样本口径：余额 ± 金额 = 下一笔余额）。 */
class BalanceChainAuditorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun parsed(
        fen: Long,
        balanceFen: Long,
        minutesAgo: Long,
        type: TransactionType = TransactionType.EXPENSE,
        cardTail: String? = "3170",
    ) = SmsParseOutcome.Parsed(
        row = ParsedBillRow(
            date = "2026-10-08",
            type = type,
            amountFen = fen,
            counterparty = null,
            paymentMethod = "网银在线",
            description = "短信消费",
            externalId = "sms:$fen-$balanceFen",
            platformCategory = null,
        ),
        channel = SmsChannel.BANK_DIRECT,
        cardTail = cardTail,
        balanceFen = balanceFen,
        confidence = 3,
        sender = "95526",
        receivedAtMs = BASE_MS - minutesAgo * 60_000L,
        contentHash = "h",
    )

    @Test
    fun `链条自洽：支出递减无缺口`() {
        val gaps = BalanceChainAuditor.audit(
            listOf(
                // 时间升序：705.91 → 605.91 → 505.91（每笔支出 100.00，链条自洽）
                parsed(fen = 10_000L, balanceFen = 70_591L, minutesAgo = 30),
                parsed(fen = 10_000L, balanceFen = 60_591L, minutesAgo = 20),
                parsed(fen = 10_000L, balanceFen = 50_591L, minutesAgo = 10),
            ),
            zone,
        )
        assertTrue("自洽链条不应报缺口，实际=$gaps", gaps.isEmpty())
    }

    @Test
    fun `链条断裂：检出缺口并给出时间区间`() {
        val gaps = BalanceChainAuditor.audit(
            listOf(
                // 上笔余额 605.91 → 本笔支出 100.00 后应为 505.91，实际 455.91 → 缺口
                parsed(fen = 10_000L, balanceFen = 60_591L, minutesAgo = 20),
                parsed(fen = 10_000L, balanceFen = 45_591L, minutesAgo = 10),
            ),
            zone,
        )
        assertEquals(1, gaps.size)
        assertTrue(gaps.first().detail.contains("疑似漏了 1 条通知"))
        assertEquals("3170", gaps.first().cardTail)
    }

    @Test
    fun `收入方向：余额递增`() {
        val gaps = BalanceChainAuditor.audit(
            listOf(
                parsed(fen = 50_000L, balanceFen = 60_591L, minutesAgo = 20),
                parsed(fen = 50_000L, balanceFen = 110_591L, minutesAgo = 10, type = TransactionType.INCOME),
            ),
            zone,
        )
        assertTrue("收入递增应自洽，实际=$gaps", gaps.isEmpty())
    }

    @Test
    fun `不同卡尾号不互相比较`() {
        val gaps = BalanceChainAuditor.audit(
            listOf(
                parsed(fen = 10_000L, balanceFen = 60_591L, minutesAgo = 20, cardTail = "3170"),
                parsed(fen = 10_000L, balanceFen = 99_999L, minutesAgo = 10, cardTail = "8888"),
            ),
            zone,
        )
        assertTrue("不同卡不应比较，实际=$gaps", gaps.isEmpty())
    }

    @Test
    fun `少于两条带余额记录：不可校验`() {
        val gaps = BalanceChainAuditor.audit(
            listOf(parsed(fen = 10_000L, balanceFen = 60_591L, minutesAgo = 10)),
            zone,
        )
        assertTrue(gaps.isEmpty())
    }

    @Test
    fun `无余额记录：跳过校验`() {
        val noBalance = parsed(fen = 10_000L, balanceFen = 0L, minutesAgo = 10).copy(balanceFen = null)
        val gaps = BalanceChainAuditor.audit(
            listOf(noBalance, noBalance.copy(receivedAtMs = noBalance.receivedAtMs - 60_000L)),
            zone,
        )
        assertTrue(gaps.isEmpty())
    }

    @Test
    fun `headline 汇总缺口条数`() {
        val gaps = BalanceChainAuditor.audit(
            listOf(
                parsed(fen = 10_000L, balanceFen = 60_591L, minutesAgo = 20),
                parsed(fen = 10_000L, balanceFen = 45_591L, minutesAgo = 10),
            ),
            zone,
        )
        val headline = BalanceChainAuditor.headline(gaps)
        assertTrue(headline != null && headline.contains("余额链疑似缺口 1 处"))
    }

    private companion object {
        /** 2026-10-09 12:00 (Asia/Shanghai)。 */
        const val BASE_MS = 1_791_518_400_000L
    }
}
