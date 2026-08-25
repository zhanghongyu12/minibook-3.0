package com.minibook.data.local.bill

import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.ImportSkipReason
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.bill.BillSourceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * 支付宝「交易流水证明」CSV 解析器单测（TASK-041，PRD §F-12 解析规则 + 过滤规则，DEC-015）。
 *
 * 造 GBK 字符串（`String.toByteArray(GBK)` → `InputStreamReader(GBK)` 还原），
 * 覆盖：动态表头探测（前导多说明行 + 空行跳过）；字段映射与 `\t`/引号处理；金额 strip
 * （`￥`/`,`）；「不计收支/空」→NEUTRAL、「交易关闭」→CLOSED、坏金额→PARSE_ERROR、
 * 空单号→PARSE_ERROR、单号 `alipay:` 前缀；缺表头→Unrecognized。
 */
class AlipayCsvParserTest {

    private val gbk: Charset = Charset.forName("GBK")

    private fun gbkReader(text: String): InputStreamReader =
        InputStreamReader(ByteArrayInputStream(text.toByteArray(gbk)), gbk)

    private fun decode(text: String): BillParseResult = AlipayCsvParser.decode(gbkReader(text))

    private fun assertSuccess(result: BillParseResult): BillParseResult.Success {
        assertTrue("expected Success but was $result", result is BillParseResult.Success)
        return result as BillParseResult.Success
    }

    @Test
    fun `GBK 解码 - 动态表头探测 - 逐行映射与过滤计数`() {
        val csv = buildString {
            appendLine("支付宝交易明细证明")
            appendLine("导出时间：2026-08-24 至 2026-08-27")
            appendLine()
            appendLine("交易时间,收/支,金额（元）,交易对方,收/付款方式,商品说明,备注,交易订单号,交易分类,交易状态")
            appendLine("2026-08-20 12:34:56,支出,88.50,星巴克,花呗,拿铁,/,20260820123456001,餐饮美食,交易成功")
            appendLine("2026-08-21 10:00:00,收入,1000,张三,余额,工资,,20260821100000002,劳动报酬,交易成功")
            appendLine("2026-08-22 09:00:00,不计收支,0.00,,,退款,,20260822090000003,退款,交易成功")
            appendLine("2026-08-23 08:00:00,支出,5.00,商家,余额,商品,备注1,20260823080000004,日用百货,交易关闭")
            appendLine("2026-08-24 07:00:00,支出,abc,商家,余额,商品,,20260824070000005,日用百货,交易成功")
            appendLine("2026-08-25 06:00:00,支出,6.50,商家,余额,\"商品,带逗号\",/,20260825060000006\t,日用百货,交易成功")
            appendLine("2026-08-26 05:00:00,支出,\"￥1,200.00\",超市,银行卡,商品说明,备注X,20260826050000007,日用百货,交易成功")
            appendLine("2026-08-27 04:00:00,,5.00,,,商品,,20260827040000008,分类,交易成功")
        }

        val success = assertSuccess(decode(csv))
        assertEquals(4, success.rows.size)
        assertEquals(2, success.skipped.count { it.reason == ImportSkipReason.NEUTRAL })
        assertEquals(1, success.skipped.count { it.reason == ImportSkipReason.CLOSED })
        assertEquals(1, success.skipped.count { it.reason == ImportSkipReason.PARSE_ERROR })

        val r0 = success.rows[0]
        assertEquals("2026-08-20", r0.date)
        assertEquals(TransactionType.EXPENSE, r0.type)
        assertEquals(8850L, r0.amountFen)
        assertEquals("星巴克", r0.counterparty)
        assertEquals("花呗", r0.paymentMethod)
        assertEquals("拿铁", r0.description)
        assertEquals("alipay:20260820123456001", r0.externalId)
        assertEquals("餐饮美食", r0.platformCategory)

        val r1 = success.rows[1]
        assertEquals(TransactionType.INCOME, r1.type)
        assertEquals(100000L, r1.amountFen)
        assertEquals("工资", r1.description)
        assertEquals("劳动报酬", r1.platformCategory)

        // 内联 `\t` 订单号尾缀被 trim（单元格 .trim()），引号包裹逗号字段按原样进商品说明。
        val r2 = success.rows[2]
        assertEquals(650L, r2.amountFen)
        assertEquals("商品,带逗号", r2.description)
        assertEquals("alipay:20260825060000006", r2.externalId)

        // 金额 strip `￥`+`,`；备注以「｜」追加进 description。
        val r3 = success.rows[3]
        assertEquals(120000L, r3.amountFen)
        assertEquals("商品说明｜备注X", r3.description)

        // PARSE_ERROR 明细含行号 + 细节。
        val parseError = success.skipped.single { it.reason == ImportSkipReason.PARSE_ERROR }
        assertEquals("金额非法: abc", parseError.detail)
    }

    @Test
    fun `缺表头返回 Unrecognized`() {
        val result = decode("这是\n一段\n没有表头,的文字\n")
        assertTrue(result is BillParseResult.Unrecognized)
    }

    @Test
    fun `非 Csv 输入返回 Unrecognized`() {
        val result = AlipayCsvParser.parse(BillSourceInput.Xlsx(File("unused.xlsx")))
        assertTrue(result is BillParseResult.Unrecognized)
    }

    @Test
    fun `金额 0 为 ZERO_AMOUNT - 负金额与空单号为 PARSE_ERROR`() {
        val csv = buildString {
            appendLine("交易时间,收/支,金额,交易订单号")
            appendLine("2026-08-20 12:00:00,支出,0.00,单号1")     // 0 元 → ZERO_AMOUNT
            appendLine("2026-08-20 12:00:00,支出,-5.00,单号2")    // 负金额（转分报错）→ PARSE_ERROR
            appendLine("2026-08-20 12:00:00,支出,5.00,   ")       // 空单号 → PARSE_ERROR
        }
        val success = assertSuccess(decode(csv))
        assertTrue(success.rows.isEmpty())
        val zero = success.skipped.single { it.reason == ImportSkipReason.ZERO_AMOUNT }
        assertEquals("金额为 0", zero.detail)
        assertEquals(2, success.skipped.count { it.reason == ImportSkipReason.PARSE_ERROR })
    }
}