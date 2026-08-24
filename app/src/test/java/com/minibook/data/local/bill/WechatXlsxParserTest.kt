package com.minibook.data.local.bill

import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.ImportSkipReason
import com.minibook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 微信「账单明细」XLSX 解析器单测（TASK-042，DEC-016 零 xlsx 库 / 纯函数可测）。
 *
 * 直接喂 `sharedStrings` + `sheet1` 的**最小 XML 字符串**断言 [WechatXmlRows] 纯解码与
 * [WechatXlsxParser.mapRows] 映射：日期序列数整数部分（禁 Double）→ `1899-12-30 + n`、
 * `金额(元)` 字面量→分、`t=s/str/inlineStr` 三种取值、`/`→NEUTRAL、单号 `wechat:` 前缀。
 */
class WechatXlsxParserTest {

    private val sharedXml = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="17" uniqueCount="17">
        |<si><t>交易时间</t></si>
        |<si><t>交易类型</t></si>
        |<si><t>交易对方</t></si>
        |<si><t>商品</t></si>
        |<si><t>收/支</t></si>
        |<si><t>金额(元)</t></si>
        |<si><t>支付方式</t></si>
        |<si><t>当前状态</t></si>
        |<si><t>交易单号</t></si>
        |<si><t>商户单号</t></si>
        |<si><t>备注</t></si>
        |<si><t>商户消费</t></si>
        |<si><t>支出</t></si>
        |<si><t>某超市</t></si>
        |<si><t>一件商品</t></si>
        |<si><t>微信红包</t></si>
        |<si><t>收入</t></si>
        |</sst>
    """.trimMargin()

    private val sheetXml = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        |<sheetData>
        |<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c><c r="G1" t="s"><v>6</v></c><c r="H1" t="s"><v>7</v></c><c r="I1" t="s"><v>8</v></c><c r="J1" t="s"><v>9</v></c><c r="K1" t="s"><v>10</v></c></row>
        |<row r="2"><c r="A2"><v>46257.7435185057</v></c><c r="B2" t="s"><v>11</v></c><c r="C2" t="s"><v>13</v></c><c r="D2" t="s"><v>14</v></c><c r="E2" t="s"><v>12</v></c><c r="F2"><v>8.70</v></c><c r="G2" t="str"><v>零钱</v></c><c r="I2"><v>100000001</v></c></row>
        |<row r="3"><c r="A3"><v>46258</v></c><c r="B3" t="s"><v>15</v></c><c r="E3" t="s"><v>16</v></c><c r="F3"><v>6.5</v></c><c r="I3"><v>100000002</v></c></row>
        |<row r="4"><c r="A4"><v>46259</v></c><c r="E4" t="inlineStr"><is><t>/</t></is></c><c r="F4"><v>1.00</v></c></row>
        |</sheetData>
        |</worksheet>
    """.trimMargin()

    private fun assertSuccess(result: BillParseResult): BillParseResult.Success {
        assertTrue("expected Success but was $result", result is BillParseResult.Success)
        return result as BillParseResult.Success
    }

    @Test
    fun `sharedStrings 解码按 si 顺序还原`() {
        val shared = WechatXmlRows.decodeSharedStrings(sharedXml)
        assertEquals(17, shared.size)
        assertEquals("交易时间", shared[0])
        assertEquals("金额(元)", shared[5])
        assertEquals("微信红包", shared[15])
    }

    @Test
    fun `sheet1 纯解码 - 日期序列 - 金额 - 三种取值 - 斜杠过滤`() {
        val shared = WechatXmlRows.decodeSharedStrings(sharedXml)
        val decodedRows = WechatXmlRows.decodeSheet(sheetXml, shared)
        assertEquals(4, decodedRows.size) // 表头 + 3 数据行

        val success = assertSuccess(WechatXlsxParser.mapRows(decodedRows))
        assertEquals(2, success.rows.size)
        assertEquals(1, success.skipped.count { it.reason == ImportSkipReason.NEUTRAL })

        // 行 2：序列数带小数（整数部分）+ t=s 共享串 + t=str 字面量。
        val r0 = success.rows[0]
        assertEquals("2026-08-23", r0.date) // 46257.7435 → 整数 46257 → 1899-12-30 + 46257 = 2026-08-23
        assertEquals(LocalDate.of(1899, 12, 30).plusDays(46257L).toString(), r0.date)
        assertEquals(TransactionType.EXPENSE, r0.type)
        assertEquals(870L, r0.amountFen)
        assertEquals("某超市", r0.counterparty)
        assertEquals("零钱", r0.paymentMethod)
        assertEquals("一件商品", r0.description)
        assertEquals("wechat:100000001", r0.externalId)
        assertEquals("商户消费", r0.platformCategory)

        // 行 3：序列数无小数 + 微信红包收入。
        val r1 = success.rows[1]
        assertEquals("2026-08-24", r1.date) // 46258 → 1899-12-30 + 46258 = 2026-08-24
        assertEquals(LocalDate.of(1899, 12, 30).plusDays(46258L).toString(), r1.date)
        assertEquals(TransactionType.INCOME, r1.type)
        assertEquals(650L, r1.amountFen)
        assertEquals("微信红包", r1.platformCategory)
        assertEquals("wechat:100000002", r1.externalId)
    }

    @Test
    fun `真实 zip 层 readEntry 贯通`() {
        val tmp = java.io.File.createTempFile("wechat", ".xlsx")
        try {
            ZipOutputStream(tmp.outputStream()).use { zip ->
                fun put(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                put("xl/sharedStrings.xml", sharedXml)
                put("xl/worksheets/sheet1.xml", sheetXml)
            }
            val success = assertSuccess(WechatXlsxParser.decode(tmp))
            assertEquals(2, success.rows.size)
        } finally {
            tmp.delete()
        }
    }
}