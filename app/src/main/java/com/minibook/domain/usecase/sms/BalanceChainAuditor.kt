package com.minibook.domain.usecase.sms

import com.minibook.domain.model.BalanceGap
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 余额链审计（F-14，设计 §3.10）——**短信通道独有**的漏检能力。
 *
 * 银行消费类通知常带账户余额，因此可用链条反查缺口：
 * ```
 * 上一笔余额 ± 本笔金额 == 本笔余额   （支出减、收入加）
 * ```
 * 不成立即报「疑似缺口」（附时间区间）。**仅作提示，不作入库门禁**（设计 §7 R10：
 * 跨行、多卡、无余额、非实时记账都会让链条失效）。
 *
 * 纯 Kotlin（无 IO / 无 Android），JVM 可单测。
 */
object BalanceChainAuditor {

    private val LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    /**
     * @param parsed 解析成功且带余额的条目（可含无余额者，自动跳过）
     * @return 缺口列表（空 = 链条自洽或不可校验）
     */
    fun audit(
        parsed: List<SmsParseOutcome.Parsed>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<BalanceGap> {
        val gaps = mutableListOf<BalanceGap>()
        // 按卡尾号分组（尾号缺失归入同一组，标注为未知卡），组内按到达时间升序。
        val groups = parsed.groupBy { it.cardTail ?: UNKNOWN_CARD }
        for ((card, items) in groups) {
            val chain = items
                .filter { it.balanceFen != null }
                .sortedBy { it.receivedAtMs }
            if (chain.size < 2) continue

            for (i in 1 until chain.size) {
                val prev = chain[i - 1]
                val curr = chain[i]
                val prevBalance = prev.balanceFen ?: continue
                val currBalance = curr.balanceFen ?: continue
                val expected = when (curr.row.type) {
                    TransactionType.EXPENSE -> prevBalance - curr.row.amountFen
                    TransactionType.INCOME -> prevBalance + curr.row.amountFen
                }
                if (expected != currBalance) {
                    gaps += BalanceGap(
                        cardTail = if (card == UNKNOWN_CARD) null else card,
                        fromLabel = label(prev.receivedAtMs, zoneId),
                        toLabel = label(curr.receivedAtMs, zoneId),
                        detail = "余额不连续：上笔 ${yuan(prevBalance)} → 本笔 ${yuan(currBalance)}，" +
                            "按本笔金额 ${yuan(curr.row.amountFen)}（${typeLabel(curr.row.type)}）推算应为 ${yuan(expected)}，" +
                            "疑似漏了 1 条通知",
                    )
                }
            }
        }
        return gaps
    }

    /** 供 UI 单行展示（缺口计数 + 首条摘要）。 */
    fun headline(gaps: List<BalanceGap>): String? =
        gaps.firstOrNull()?.let { "余额链疑似缺口 ${gaps.size} 处，最早 ${it.fromLabel}~${it.toLabel}" }

    private const val UNKNOWN_CARD = "__unknown__"

    private fun label(epochMs: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(LABEL_FORMAT)

    private fun yuan(fen: Long): String = "${fen / 100}.${(fen % 100).toString().padStart(2, '0')} 元"

    private fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
    }
}
