package com.minibook.data.local.bill

import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.ImportSkipReason
import com.minibook.domain.model.ParseSkippedRow
import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.bill.BillParser
import com.minibook.domain.usecase.bill.BillSourceInput
import java.io.File
import java.util.zip.ZipFile

/**
 * 微信「账单明细」XLSX 解析器（F-12，DEC-016 零第三方 xlsx 库，ARCHITECTURE §16.5）。
 *
 * 读取 `xl/sharedStrings.xml` + `xl/worksheets/sheet1.xml`，交纯函数 [WechatXmlRows] 解码为
 * 「绝对列序 → 文本」的稀疏行，再按动态表头探测（[BillHeaderMatcher]）映射列：只依赖
 * `java.util.zip.ZipFile`（JDK，JVM 可测），不 import android.*。XML 解码本身在 [WechatXmlRows]
 * 为可测纯函数（单测直接喂 XML 字符串，DEC-016 #7）。
 *
 * 微信字段映射（PRD §F-12）：
 * - 交易时间 = Excel 日期序列数（如 `46257.7435`）→ 整数部分 = 距 1899-12-30 的天数
 *   （`LocalDate.of(1899,12,30).plusDays(n)`，时分秒丢弃，**禁 Double**）；
 * - 金额(元) = `<v>` 文本字面量（不 parse 为 double）→ strip → `MoneyFormatter.toFen`；
 * - 收/支 = 「支出/收入」，`/`/空 → NEUTRAL；交易单号 → `wechat:<单号>`；
 * - 商品 → description 主、备注非空且 ≠「/」以 `｜` 追加；交易对方 → counterparty；
 *   支付方式 → paymentMethod；交易类型 → platformCategory；「当前状态/对方账号/商户单号」不落库。
 */
object WechatXlsxParser : BillParser {

    override val format: TransactionSource = TransactionSource.WECHAT_IMPORT

    override fun parse(input: BillSourceInput): BillParseResult {
        val file = (input as? BillSourceInput.Xlsx)?.file
            ?: return BillParseResult.Unrecognized("微信账单需 XLSX 输入")
        return decode(file)
    }

    /** 核心解码（[File] → 结果；单测可走 [mapRows] 跳过 zip 层）。 */
    fun decode(file: File): BillParseResult = try {
        ZipFile(file).use { zip ->
            val sharedXml = readEntry(zip, SHARED_STRINGS_ENTRY)
            val sheetXml = readEntry(zip, SHEET_ENTRY)
                ?: return BillParseResult.Unrecognized("缺少 $SHEET_ENTRY")
            val sharedStrings = sharedXml?.let(WechatXmlRows::decodeSharedStrings) ?: emptyList()
            mapRows(WechatXmlRows.decodeSheet(sheetXml, sharedStrings))
        }
    } catch (e: Exception) {
        BillParseResult.Unrecognized("XLSX 解析失败: ${e.message}")
    }

    /** 已解码行 → 结果（动态表头探测 + 逐行映射/过滤；纯函数，供单测断言日期序列→date 等）。 */
    internal fun mapRows(decodedRows: List<Map<Int, String>>): BillParseResult {
        val rows = mutableListOf<ParsedBillRow>()
        val skipped = mutableListOf<ParseSkippedRow>()
        var columns: Map<BillColumn, Int>? = null
        var rowNumber = 0

        for (cells in decodedRows) {
            rowNumber++
            if (columns == null) {
                columns = BillHeaderMatcher.mapColumnsByIndex(cells)
                continue
            }
            when (val outcome = parseRow(cells, columns!!, rowNumber)) {
                is RowOutcome.Ok -> rows += outcome.row
                is RowOutcome.Skip -> skipped += ParseSkippedRow(rowNumber, outcome.reason, outcome.detail)
            }
        }

        if (columns == null) {
            return BillParseResult.Unrecognized("未找到表头（缺少 交易时间/收/支/金额 列）")
        }
        return BillParseResult.Success(rows, skipped)
    }

    private sealed interface RowOutcome {
        data class Ok(val row: ParsedBillRow) : RowOutcome
        data class Skip(val reason: ImportSkipReason, val detail: String) : RowOutcome
    }

    private fun parseRow(cells: Map<Int, String>, columns: Map<BillColumn, Int>, rowNumber: Int): RowOutcome {
        fun cell(column: BillColumn): String {
            val index = columns[column] ?: return ""
            return cells[index]?.trim().orEmpty()
        }

        // 1) 收/支：`/`/空/中性 → NEUTRAL。
        val directionRaw = cell(BillColumn.TYPE)
        val type: TransactionType = BillText.mapDirection(directionRaw)
            ?: return RowOutcome.Skip(ImportSkipReason.NEUTRAL, "收/支=${directionRaw.ifEmpty { "/" }}")

        // 2) 交易时间：Excel 序列数（整数部分=距 1899-12-30 天数），或日期文本。
        val date: String = resolveDate(cell(BillColumn.DATE))
            ?: return RowOutcome.Skip(ImportSkipReason.PARSE_ERROR, "交易时间缺失或非法")

        // 3) 金额：字面量转分；非法/非正 → PARSE_ERROR。
        val amountRaw = cell(BillColumn.AMOUNT)
        val amountFen: Long? = BillText.amountToFen(amountRaw)
        if (amountFen == null || amountFen <= 0L) {
            return RowOutcome.Skip(ImportSkipReason.PARSE_ERROR, "金额非法或非正: ${amountRaw.ifEmpty { "空" }}")
        }

        // 4) 交易单号 → externalId；空 → PARSE_ERROR。
        val orderNo = cell(BillColumn.ORDER_NO)
        if (orderNo.isEmpty()) {
            return RowOutcome.Skip(ImportSkipReason.PARSE_ERROR, "交易单号为空")
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
                externalId = "wechat:$orderNo",
                platformCategory = platformCategory,
            ),
        )
    }

    /**
     * 微信交易时间解析（DEC-016 #4）：Excel 日期序列数 `46257.7435` 取整数部分 `n`，
     * `date = LocalDate.of(1899,12,30).plusDays(n)`（时分秒丢弃）；全程字符串拆分、
     * **禁 Double**。非序列数 → 回退按日期文本归一化（[BillText.normalizeDate]）。
     */
    private fun resolveDate(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val integerPart = s.substringBefore('.')
        return try {
            java.time.LocalDate.of(1899, 12, 30).plusDays(integerPart.toLong()).toString()
        } catch (e: NumberFormatException) {
            BillText.normalizeDate(s)
        }
    }

    private fun readEntry(zip: ZipFile, name: String): String? {
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private const val SHARED_STRINGS_ENTRY = "xl/sharedStrings.xml"
    private const val SHEET_ENTRY = "xl/worksheets/sheet1.xml"
}