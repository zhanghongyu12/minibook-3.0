package com.minibook.data.local.bill

import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.ImportSkipReason
import com.minibook.domain.model.ParseSkippedRow
import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.bill.BillParser
import com.minibook.domain.usecase.bill.BillSourceInput
import java.io.Reader

/**
 * 支付宝「交易流水证明」CSV 解析器（F-12，DEC-015/017，ARCHITECTURE §16.4）。
 *
 * 纯 Kotlin、无 android 依赖（GBK 解码由数据层 [com.minibook.data.local.bill.SafBillFilePreparer]
 * 以 `InputStreamReader(stream, GBK)` 完成，本类只面向已解码的 [Reader]），JVM 可单测。
 *
 * 要点：
 * - **动态表头探测**（PRD §F-12 解析规则 1）：逐行扫描直到 [BillHeaderMatcher] 命中
 *   「交易时间 + 收/支 + 金额」三必需列，前导说明行数量变化不影响解析；
 * - 字段 `trim()`（含交易订单号尾部 `\t`）；金额 `strip` 后 `MoneyFormatter.toFen`；
 * - 过滤（行级，逐条计数）：收/支=不计收支/「/」/空→NEUTRAL；交易状态=交易关闭→CLOSED；
 *   金额为 0→ZERO_AMOUNT；时间缺失/金额非法(负或非数字)/交易订单号空→PARSE_ERROR（PRD §F-12 过滤规则）。
 */
object AlipayCsvParser : BillParser {

    override val format: TransactionSource = TransactionSource.ALIPAY_IMPORT

    override fun parse(input: BillSourceInput): BillParseResult {
        val reader = (input as? BillSourceInput.Csv)?.reader
            ?: return BillParseResult.Unrecognized("支付宝账单需 CSV 输入")
        return decode(reader)
    }

    /** 核心解码（纯 [Reader] → 结果，供单测直接喂 GBK 已解码字符串的 [StringReader]）。 */
    fun decode(reader: Reader): BillParseResult {
        val rows = mutableListOf<ParsedBillRow>()
        val skipped = mutableListOf<ParseSkippedRow>()
        var columns: Map<BillColumn, Int>? = null
        var lineNo = 0

        reader.buffered().useLines { lines ->
            for (line in lines) {
                lineNo++
                if (line.isBlank()) continue
                val cells = splitCsvLine(line)
                if (columns == null) {
                    // 动态表头探测：非表头行（前导说明）继续扫描。
                    columns = BillHeaderMatcher.mapColumns(cells.map { it.trim() })
                    continue
                }
                when (val outcome = parseRow(cells, columns!!, lineNo)) {
                    is RowOutcome.Ok -> rows += outcome.row
                    is RowOutcome.Skip -> skipped += ParseSkippedRow(lineNo, outcome.reason, outcome.detail)
                }
            }
        }

        if (columns == null) {
            return BillParseResult.Unrecognized("未找到表头（缺少 交易时间/收/支/金额 列）")
        }
        return BillParseResult.Success(rows, skipped)
    }

    /** 单行解析结果：有效行或跳过（原因 + 细节）。 */
    private sealed interface RowOutcome {
        data class Ok(val row: ParsedBillRow) : RowOutcome
        data class Skip(val reason: ImportSkipReason, val detail: String) : RowOutcome
    }

    private fun parseRow(cells: List<String>, columns: Map<BillColumn, Int>, lineNo: Int): RowOutcome {
        fun cell(column: BillColumn): String {
            val index = columns[column] ?: return ""
            return cells.getOrElse(index) { "" }.trim()
        }

        // 1) 收/支：不计收支/「/」/空 → NEUTRAL（PRD：退款成功/还款成功均带「不计收支」，已被此条覆盖）。
        val directionRaw = cell(BillColumn.TYPE)
        val type: TransactionType = BillText.mapDirection(directionRaw)
            ?: return RowOutcome.Skip(ImportSkipReason.NEUTRAL, "收/支=${directionRaw.ifEmpty { "空" }}")

        // 2) 交易状态 = 交易关闭 → CLOSED。
        if (cell(BillColumn.STATUS) == "交易关闭") {
            return RowOutcome.Skip(ImportSkipReason.CLOSED, "交易状态=交易关闭")
        }

        // 3) 交易时间 → yyyy-MM-dd；缺失/非法 → PARSE_ERROR。
        val date: String = BillText.normalizeDate(cell(BillColumn.DATE))
            ?: return RowOutcome.Skip(ImportSkipReason.PARSE_ERROR, "交易时间缺失或非法")

        // 4) 金额：strip 后转分；非法(负或非数字) → PARSE_ERROR；0 分 → ZERO_AMOUNT（0 元购/退款归零无需记账）。
        val amountRaw = cell(BillColumn.AMOUNT)
        val amountFen: Long? = BillText.amountToFen(amountRaw)
        if (amountFen == null) {
            return RowOutcome.Skip(ImportSkipReason.PARSE_ERROR, "金额非法: ${amountRaw.ifEmpty { "空" }}")
        }
        if (amountFen == 0L) {
            return RowOutcome.Skip(ImportSkipReason.ZERO_AMOUNT, "金额为 0")
        }

        // 5) 交易订单号 → externalId；空 → PARSE_ERROR（DEC-015 #6）。
        val orderNo = cell(BillColumn.ORDER_NO)
        if (orderNo.isEmpty()) {
            return RowOutcome.Skip(ImportSkipReason.PARSE_ERROR, "交易订单号为空")
        }

        val counterparty = cell(BillColumn.COUNTERPARTY).ifEmpty { null }
        val paymentMethod = cell(BillColumn.PAYMENT_METHOD).ifEmpty { null }
        val description = BillText.buildDescription(cell(BillColumn.GOODS), cell(BillColumn.REMARK))
        val platformCategory = cell(BillColumn.PLATFORM_CATEGORY).ifEmpty { null }

        return RowOutcome.Ok(
            ParsedBillRow(
                date = date,
                type = type,
                amountFen = amountFen,
                counterparty = counterparty,
                paymentMethod = paymentMethod,
                description = description,
                externalId = "alipay:$orderNo",
                platformCategory = platformCategory,
            ),
        )
    }

    /**
     * 逗号分隔 + 双引号包裹（RFC 4180 子集）：字段内逗号、转义引号（`""`）正确处理。
     * **按行读取，不支持字段内嵌换行**——上层 [decode] 以 `useLines` 逐行读，引号包裹的
     * 多行字段会被跨行拆断（支付宝「交易流水证明」个人对账 CSV 无此场景，故不夸大支持）。
     * 切分后再 trim。
     */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' -> {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> current.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    result.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}