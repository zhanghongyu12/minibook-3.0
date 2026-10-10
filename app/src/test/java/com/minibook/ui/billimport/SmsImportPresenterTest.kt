package com.minibook.ui.billimport

import com.minibook.domain.model.BalanceGap
import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.SmsAmbiguousHit
import com.minibook.domain.model.SmsChannel
import com.minibook.domain.model.SmsDuplicateHit
import com.minibook.domain.model.SmsImportSummary
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.SmsPendingItem
import com.minibook.domain.model.SmsScanMode
import com.minibook.domain.model.SmsScanPreview
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-14 导入页 presenter 单测（纯函数）。
 *
 * 覆盖设计「不遗漏」的**展示侧口径**：干跑 headline 必须与计数闭合等式一致
 * （`扫描 N = 可导入 X + 未识别 Y + 非账单 Z + 已存在/重复 W`），且隐私纪律
 * （只出现脱敏摘要，不出现短信原文/卡号/余额）由 fixture 可断言。
 */
class SmsImportPresenterTest {

    private fun row(
        description: String = "短信消费·尾号3170·网银在线",
        fen: Long = 10_000L,
        date: String = "2026-10-08",
    ) = ParsedBillRow(
        date = date,
        type = TransactionType.EXPENSE,
        amountFen = fen,
        counterparty = null,
        paymentMethod = "网银在线",
        description = description,
        externalId = "sms:$description$fen",
        platformCategory = null,
    )

    private fun parsed(row: ParsedBillRow = row()) = SmsParseOutcome.Parsed(
        row = row,
        channel = SmsChannel.BANK_DIRECT,
        cardTail = "3170",
        balanceFen = 60_591L,
        confidence = 3,
        sender = "95526",
        receivedAtMs = 1_791_518_400_000L,
        contentHash = "hash",
    )

    /** 计数闭合口径：planned 2 + 未识别 2 + 非账单 1 = 8；已解析 5 = 可导入 2 + 已存在 1 + 判重 1 + 疑似 1。 */
    private fun preview(
        importable: List<SmsParseOutcome.Parsed> = listOf(parsed(), parsed(row(fen = 20_000L))),
        existing: List<SmsParseOutcome.Parsed> = listOf(parsed(row(fen = 30_000L))),
        duplicates: List<SmsDuplicateHit> = listOf(
            SmsDuplicateHit(parsed(row(fen = 40_000L)).row, "t1", TransactionSource.ALIPAY_IMPORT, "2026-10-08", "重复"),
        ),
        ambiguous: List<SmsAmbiguousHit> = listOf(
            SmsAmbiguousHit(parsed(row(fen = 50_000L)).row, emptyList()),
        ),
        unrecognized: List<SmsPendingItem> = listOf(
            SmsPendingItem(1L, "95526", 0L, "摘要：金额 100.00 元", "未找到交易日期"),
            SmsPendingItem(2L, "95526", 0L, "摘要：金额 20.00 元", "未找到收支方向"),
        ),
        notBillReasons: List<String> = listOf("验证码类"),
        scanTotal: Int = 8,
        gaps: List<BalanceGap> = emptyList(),
    ): SmsScanPreview = SmsScanPreview(
        scanTotal = scanTotal,
        importable = importable,
        existing = existing,
        duplicates = duplicates,
        ambiguous = ambiguous,
        unrecognized = unrecognized,
        notBillReasons = notBillReasons,
        balanceGaps = gaps,
    ).also { it.assertClosed() }

    @Test
    fun `干跑 headline 与计数闭合等式一致`() {
        assertEquals(
            "扫描 8 条 = 可导入 2 + 未识别 2 + 非账单 1 + 已存在/重复 3",
            SmsImportPresenter.previewHeadline(preview()),
        )
    }

    @Test
    fun `结果 headline 四类计数齐全`() {
        val summary = SmsImportSummary(
            scanTotal = 8,
            parsed = 5,
            imported = 2,
            existing = 1,
            duplicates = 1,
            ambiguous = 1,
            unrecognized = 2,
            notBill = 1,
        )
        val headline = SmsImportPresenter.resultHeadline(summary)
        assertTrue(headline.contains("新增 2 笔"))
        assertTrue(headline.contains("已存在 1 条"))
        assertTrue(headline.contains("判重跳过 1 条"))
        assertTrue(headline.contains("疑似重复 1 条"))
    }

    @Test
    fun `逐条结果行四类标记齐全且用脱敏摘要`() {
        val lines = SmsImportPresenter.lines(preview())
        assertEquals(2 + 1 + 1 + 1 + 2, lines.size)
        assertTrue(lines.any { it.startsWith("✅ 可导入") })
        assertTrue(lines.any { it.contains("与已有记录重复，已跳过") })
        assertTrue(lines.any { it.contains("与「支付宝」记录重复，已跳过") })
        assertTrue(lines.any { it.startsWith("🔁") })
        assertTrue(lines.any { it.startsWith("⚠ 未识别") })
    }

    @Test
    fun `逐条结果行超上限时截断并提示`() {
        val many = (1..SmsImportPresenter.MAX_LINES + 5).map { parsed(row(description = "短信消费 #$it", fen = it * 100L)) }
        val lines = SmsImportPresenter.lines(
            preview(
                importable = many,
                existing = emptyList(),
                duplicates = emptyList(),
                ambiguous = emptyList(),
                unrecognized = emptyList(),
                notBillReasons = List(5) { "非账单" },
                scanTotal = many.size + 5,
            ),
        )
        assertEquals(SmsImportPresenter.MAX_LINES + 1, lines.size)
        assertEquals(SmsImportPresenter.OMITTED_HINT, lines.last())
    }

    @Test
    fun `扫描中前三步同时 RUNNING（如实反映一次调用）`() {
        val states = SmsImportPresenter.stepStates(SmsImportPhase.SCANNING, preview = null, summary = null, failedStep = null)
        assertEquals(SmsImportStep.entries.size, states.size)
        assertEquals(SmsImportStepStatus.RUNNING, status(states, SmsImportStep.READ))
        assertEquals(SmsImportStepStatus.RUNNING, status(states, SmsImportStep.PARSE))
        assertEquals(SmsImportStepStatus.RUNNING, status(states, SmsImportStep.DEDUP))
        assertEquals(SmsImportStepStatus.PENDING, status(states, SmsImportStep.WRITE))
        assertEquals(SmsImportStepStatus.PENDING, status(states, SmsImportStep.SUMMARY))
    }

    @Test
    fun `预览态前三步 DONE 且带摘要，写入与汇总 PENDING`() {
        val states = SmsImportPresenter.stepStates(SmsImportPhase.PREVIEW, preview(), summary = null, failedStep = null)
        assertEquals(SmsImportStepStatus.DONE, status(states, SmsImportStep.READ))
        assertEquals("扫描 8 条", states.first { it.step == SmsImportStep.READ }.summary)
        assertEquals("识别 5 条", states.first { it.step == SmsImportStep.PARSE }.summary)
        assertEquals("重复/已存在 3 条", states.first { it.step == SmsImportStep.DEDUP }.summary)
        assertEquals(SmsImportStepStatus.PENDING, status(states, SmsImportStep.WRITE))
        // PENDING 步不渲染摘要
        assertEquals(null, states.first { it.step == SmsImportStep.WRITE }.summary)
    }

    @Test
    fun `失败态以失败步为界：之前 DONE、该步 FAILED、之后 PENDING`() {
        val states = SmsImportPresenter.stepStates(
            SmsImportPhase.ERROR,
            preview = null,
            summary = null,
            failedStep = SmsImportStep.WRITE,
        )
        assertEquals(SmsImportStepStatus.DONE, status(states, SmsImportStep.READ))
        assertEquals(SmsImportStepStatus.DONE, status(states, SmsImportStep.DEDUP))
        assertEquals(SmsImportStepStatus.FAILED, status(states, SmsImportStep.WRITE))
        assertEquals(SmsImportStepStatus.PENDING, status(states, SmsImportStep.SUMMARY))
    }

    @Test
    fun `失败步未知：全部 PENDING（不谎报进度）`() {
        val states = SmsImportPresenter.stepStates(SmsImportPhase.ERROR, null, null, failedStep = null)
        assertTrue(states.all { it.status == SmsImportStepStatus.PENDING })
    }

    @Test
    fun `完成态：五步全 DONE 且带写入与汇总摘要`() {
        val summary = SmsImportSummary(
            scanTotal = 8,
            parsed = 5,
            imported = 2,
            existing = 1,
            duplicates = 1,
            ambiguous = 1,
            unrecognized = 2,
            notBill = 1,
        )
        val states = SmsImportPresenter.stepStates(SmsImportPhase.DONE, preview(), summary, failedStep = null)
        assertTrue(states.all { it.status == SmsImportStepStatus.DONE })
        assertEquals("写入 2 笔", states.first { it.step == SmsImportStep.WRITE }.summary)
        assertEquals("新增 2 笔，跳过 2 条", states.first { it.step == SmsImportStep.SUMMARY }.summary)
    }

    @Test
    fun `余额链文案：无缺口与有缺口`() {
        assertEquals("余额链：无缺口", SmsImportPresenter.balanceLine(emptyList()))
        val gaps = listOf(BalanceGap("3170", "10-08 19:45", "10-09 11:17", "疑似漏了 1 条通知"))
        val line = SmsImportPresenter.balanceLine(gaps)
        assertTrue(line.contains("缺口"))
        // 隐私：余额链文案不含余额数值
        assertTrue(!line.contains("605.91") && !line.contains("余额 "))
    }

    @Test
    fun `待确认条数 = 未看过的未识别 + 疑似重复（已看过不计，2026-10-10 二次反馈）`() {
        assertEquals(3, SmsImportPresenter.pendingCount(preview()))
        assertEquals(0, SmsImportPresenter.pendingCount(null))
        // 未识别条目全部已看过 → 数字归零（不再「永远消除不掉」）；疑似重复仍需人工判定，故保留
        val allSeen = preview(unrecognized = preview().unrecognized.map { it.copy(seen = true) })
        assertEquals(preview().ambiguous.size, SmsImportPresenter.pendingCount(allSeen))
        // 混合：1 条已看过 + 1 条新 → 只报新那条
        val mixed = preview(
            unrecognized = listOf(
                preview().unrecognized.first().copy(seen = true),
                preview().unrecognized.last(),
            ),
        )
        assertEquals(1 + preview().ambiguous.size, SmsImportPresenter.pendingCount(mixed))
    }

    @Test
    fun `已看过的未识别条目移出主列表、进折叠区（2026-10-10 用户要求）`() {
        val seenItem = SmsPendingItem(1L, "95526", 0L, "摘要：金额 100.00 元", "未找到交易日期", seen = true)
        val freshItem = SmsPendingItem(2L, "95526", 0L, "摘要：金额 20.00 元", "未找到收支方向")
        val p = preview(unrecognized = listOf(seenItem, freshItem))

        val lines = SmsImportPresenter.lines(p)
        assertTrue(lines.any { it.contains("未找到收支方向") })
        assertTrue(!lines.any { it.contains("未找到交易日期") })

        assertEquals(
            listOf("⚠ 未识别（未找到交易日期）：摘要：金额 100.00 元"),
            SmsImportPresenter.seenLines(p),
        )
        // 口径不变：计数仍按全量（折叠只影响展示分组，不影响「不遗漏」的分子分母）
        assertEquals(2, p.unrecognized.size)
        assertEquals(8, p.scanTotal)
    }

    @Test
    fun `未识别行文案统一：主列表与折叠区同一格式`() {
        val item = SmsPendingItem(9L, "95533", 0L, "摘要：支出 77.00 元", "发送方未登记模板")
        assertEquals(
            "⚠ 未识别（发送方未登记模板）：摘要：支出 77.00 元",
            SmsImportPresenter.unrecognizedLine(item),
        )
    }

    @Test
    fun `扫描方式说明行：全量与增量（时间节点 = 扫描时刻而非短信时间）`() {
        val since = LocalDateTime.of(2026, 10, 10, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val scannedAt = LocalDateTime.of(2026, 10, 10, 12, 30)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 全量：与时间节点无关
        assertEquals("全量扫描：列出全部历史短信", SmsImportPresenter.scanModeLine(SmsScanMode.FULL, 0L))
        assertEquals(
            "全量扫描：列出全部历史短信",
            SmsImportPresenter.scanModeLine(SmsScanMode.FULL, since, scannedAt),
        )
        // 增量常规：本次覆盖起点 = 上次扫描时间；节点更新为**本次扫描时间**（不是短信时间）
        assertEquals(
            "增量扫描：本次覆盖 10-10 12:00 之后的新短信，起点已更新为 10-10 12:30",
            SmsImportPresenter.scanModeLine(SmsScanMode.INCREMENTAL, since, scannedAt),
        )
        // 增量首次：起点 = 本次扫描时间（空库也能立起来，不再卡在「最后一条短信的时间」）
        assertEquals(
            "增量扫描：首次使用，起点已设为本次扫描时间 10-10 12:30（历史短信请用「全量扫描」）",
            SmsImportPresenter.scanModeLine(SmsScanMode.INCREMENTAL, 0L, scannedAt),
        )
        // 防御：拿不到扫描时刻时不显示 1970 年，退回只讲覆盖范围
        assertEquals(
            "增量扫描：只看 10-10 12:00 之后的新短信",
            SmsImportPresenter.scanModeLine(SmsScanMode.INCREMENTAL, since, 0L),
        )
        assertTrue(
            SmsImportPresenter.scanModeLine(SmsScanMode.INCREMENTAL, 0L, 0L).contains("首次使用已建立水位基线"),
        )
    }

    @Test
    fun `预览携带扫描方式与时间节点：计数闭合不受影响`() {
        val full = preview()
        assertEquals(SmsScanMode.FULL, full.scanMode)
        assertEquals(0L, full.sinceMs)
        assertEquals(0L, full.scannedAtMs)

        val inc = full.copy(
            scanMode = SmsScanMode.INCREMENTAL,
            sinceMs = 1_760_000_000_000L,
            scannedAtMs = 1_760_001_800_000L,
        )
        assertEquals(SmsScanMode.INCREMENTAL, inc.scanMode)
        assertEquals(1_760_000_000_000L, inc.sinceMs)
        assertEquals(1_760_001_800_000L, inc.scannedAtMs)
        // 扫描方式与时间节点只是元信息：闭合断言与折叠口径都不变
        inc.assertClosed()
        assertEquals(full.scanTotal, inc.scanTotal)
        assertEquals(full.unrecognized.size, inc.unrecognized.size)
    }

    private fun status(states: List<SmsImportStepState>, step: SmsImportStep): SmsImportStepStatus =
        states.first { it.step == step }.status
}
