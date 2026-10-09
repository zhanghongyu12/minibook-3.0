package com.minibook.domain.usecase.mail

import com.minibook.domain.model.BillPlatform
import com.minibook.domain.model.MailBillHeader
import com.minibook.domain.model.MailBillMail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MailBillSelector] 单测（F-13「每种账单只取最近一封」）。
 *
 * 覆盖：空输入、每平台一封（无跳过）、同平台多封只留最新、同毫秒并列按主题字典序兜底
 * 且与输入顺序无关、真机窗口形态（2 微信 + 3 支付宝 → 只留 2 封）、kept/skipped 保持输入顺序。
 */
class MailBillSelectorTest {

    private fun mail(
        platform: BillPlatform,
        subject: String,
        sentDateMillis: Long,
        messageNumber: Int = 1,
    ) = MailBillMail(
        header = MailBillHeader(
            platform = platform,
            subject = subject,
            sentDateMillis = sentDateMillis,
            messageNumber = messageNumber,
        ),
        attachmentFileName = null,
        attachmentBytes = null,
        bodyText = "",
    )

    private val alipayOld = mail(BillPlatform.ALIPAY, "支付宝交易流水明细(20260501-20260824)", 1_700_000_000_000L, 1)
    private val alipayMid = mail(BillPlatform.ALIPAY, "支付宝交易流水明细(20260606-20260906)", 1_700_000_500_000L, 2)
    private val alipayNew = mail(BillPlatform.ALIPAY, "支付宝交易流水明细(20260619-20260919)", 1_700_000_900_000L, 3)
    private val wechatOld = mail(BillPlatform.WECHAT, "微信支付-账单流水文件(20260619-20260919)", 1_700_000_100_000L, 4)
    private val wechatNew = mail(BillPlatform.WECHAT, "微信支付-账单流水文件(20260623-20260923)", 1_700_001_000_000L, 5)

    @Test
    fun `空列表两侧皆空`() {
        val selection = MailBillSelector.select(emptyList())
        assertEquals(0, selection.hitCount)
        assertTrue(selection.kept.isEmpty())
        assertTrue(selection.skipped.isEmpty())
    }

    @Test
    fun `每平台各一封时全部保留且无跳过`() {
        val selection = MailBillSelector.select(listOf(wechatNew, alipayNew))
        assertEquals(listOf(wechatNew, alipayNew), selection.kept) // 保持输入顺序
        assertTrue(selection.skipped.isEmpty())
        assertEquals(2, selection.hitCount)
    }

    @Test
    fun `同平台多封只保留发送时间最新的一封`() {
        val selection = MailBillSelector.select(listOf(alipayOld, alipayNew, alipayMid))
        assertEquals(listOf(alipayNew), selection.kept)
        assertEquals(listOf(alipayOld, alipayMid), selection.skipped)
    }

    @Test
    fun `真机窗口形态两微信三支付宝只处理两封`() {
        val mails = listOf(wechatOld, alipayOld, alipayNew, wechatNew, alipayMid)
        val selection = MailBillSelector.select(mails)
        assertEquals(5, selection.hitCount)
        assertEquals(2, selection.kept.size)
        assertEquals(3, selection.skipped.size)
        assertEquals(listOf(alipayNew, wechatNew), selection.kept) // 输入顺序中的第 3、4 封
        assertEquals(listOf(wechatOld, alipayOld, alipayMid), selection.skipped)
    }

    @Test
    fun `同毫秒并列按主题字典序取大者且与输入顺序无关`() {
        val a = mail(BillPlatform.ALIPAY, "支付宝交易流水明细(A)", 1_700_000_000_000L)
        val b = mail(BillPlatform.ALIPAY, "支付宝交易流水明细(B)", 1_700_000_000_000L)
        assertEquals(listOf(b), MailBillSelector.select(listOf(a, b)).kept)
        assertEquals(listOf(b), MailBillSelector.select(listOf(b, a)).kept)
        assertEquals(listOf(a), MailBillSelector.select(listOf(a, b)).skipped)
    }

    @Test
    fun `单平台多封时仅保留一封`() {
        val selection = MailBillSelector.select(listOf(wechatOld, wechatNew))
        assertEquals(listOf(wechatNew), selection.kept)
        assertEquals(listOf(wechatOld), selection.skipped)
        assertEquals(selection.hitCount, selection.kept.size + selection.skipped.size)
    }
}
