package com.minibook.domain.usecase.sms

import com.minibook.data.datasource.TransactionDataSource
import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionSource
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 跨源去重匹配器（F-14，设计 §3.6；DEC-025）。
 *
 * **问题**：`sms:` 与 `alipay:`/`wechat:` 命名空间不同，`UNIQUE(ledgerId, externalId)` 拦不住
 * 跨源重复（同一笔先由短信记一次、导账单文件又记一次）。
 *
 * **规则（不加数据库字段，按天匹配）**：
 * ```
 * 候选 = 同账本 ∧ 同方向(type) ∧ 金额完全相等(分) ∧ 日期(天)相同
 * ├─ 恰好 1 条 → 自动判重（跳过并留痕）
 * ├─ ≥2 条   → 疑似重复（人工确认，不自动写）
 * └─ 0 条    → 正常入库
 * ```
 * **顺序无关**：短信导入时反查 `alipay:`/`wechat:`/`manual` 行；账单文件导入时反查 `sms:` 行
 * —— 两个入口调用**同一个** [SmsMatchRules.decide]（F-12/F-13 入口后续接入）。
 *
 * **已知盲区**：跨天错位（23:59 / 次日 00:01、银行记账日 ≠ 支付日）会漏判；二期以
 * `occurredAt` 列 + ±10 分钟窗口解决（设计 §7 R2）。
 */
class SmsBillMatcher @Inject constructor(
    private val transactionDataSource: TransactionDataSource,
) {

    /**
     * 查询同账本内「同 type + 同日期 + 同金额」的已有交易（走既有 `TransactionFilter` 精确查询，
     * 不新增数据源方法、不逐条查库）。
     */
    suspend fun findCandidates(ledgerId: String, row: ParsedBillRow): List<Transaction> =
        transactionDataSource.observeByLedger(
            ledgerId,
            TransactionFilter(
                type = row.type,
                dateFrom = row.date,
                dateTo = row.date,
                amountMin = row.amountFen,
                amountMax = row.amountFen,
            ),
        ).first()

    /** 查询 + 判定（入库路径使用）。 */
    suspend fun decide(
        ledgerId: String,
        row: ParsedBillRow,
        incomingSource: TransactionSource,
    ): SmsMatchDecision = SmsMatchRules.decide(
        row = row,
        incomingSource = incomingSource,
        candidates = findCandidates(ledgerId, row),
    )
}

/** 匹配判定三态。 */
sealed interface SmsMatchDecision {

    /** 唯一候选：判为跨源重复（**只跳过，永不自动删**）。 */
    data class Duplicate(val matched: Transaction, val reason: String) : SmsMatchDecision

    /** 多候选：疑似重复，进人工确认。 */
    data class Ambiguous(val candidates: List<Transaction>) : SmsMatchDecision

    /** 无候选：正常入库。 */
    data object New : SmsMatchDecision
}

/** 纯判定规则（无 Android/无 IO，JVM 可单测）。 */
object SmsMatchRules {

    /**
     * @param incomingSource 本次导入行的来源；**同来源不参与匹配**（同源重复由 `externalId` 幂等负责，
     *        否则会把同一批短信彼此判成重复）。
     */
    fun decide(
        row: ParsedBillRow,
        incomingSource: TransactionSource,
        candidates: List<Transaction>,
    ): SmsMatchDecision {
        val crossSource = candidates.filter { it.source != incomingSource }
        return when (crossSource.size) {
            0 -> SmsMatchDecision.New
            1 -> {
                val matched = crossSource.first()
                SmsMatchDecision.Duplicate(
                    matched = matched,
                    reason = "同账本同方向、金额 ${formatFen(row.amountFen)} 元、日期 ${row.date} 已存在（来源：${sourceLabel(matched.source)}）",
                )
            }
            else -> SmsMatchDecision.Ambiguous(crossSource)
        }
    }

    fun sourceLabel(source: TransactionSource): String = when (source) {
        TransactionSource.MANUAL -> "手动记账"
        TransactionSource.WECHAT_IMPORT -> "微信账单导入"
        TransactionSource.ALIPAY_IMPORT -> "支付宝账单导入"
        TransactionSource.SMS_IMPORT -> "短信导入"
    }

    fun formatFen(fen: Long): String = "${fen / 100}.${(fen % 100).toString().padStart(2, '0')}"
}
