package com.minibook.data.local.bill

import com.minibook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 账单解析共享文本/数值工具单测（F-12，PRD §F-12 金额 strip / DEC-015 description 200 截断）。
 *
 * 补齐验收口径的边界分支：
 * - 金额 strip `¥/￥/,/，/\u00A0` 等宽字符/千分位（Original 仅测 `￥`+`,`）；
 * - description ≤200 截断、备注=「/」不追加、仅备注无商品；
 * - 日期归一化兼容 `2026/8/24`（月/日 1 位）与非法输入；
 * - 收/支映射「支出/收入/其余中性」。
 */
class BillTextTest {

    // ---- 金额 strip（PRD §F-12：剥 `￥/¥/,`，含全角逗号与不换行空格） ----

    @Test
    fun `金额 strip 清理货币符与千分逗号`() {
        assertEquals(650L, BillText.amountToFen("¥6.5"))
        assertEquals(650L, BillText.amountToFen("￥6.5"))
        assertEquals(200L, BillText.amountToFen("2"))
        assertEquals(120050L, BillText.amountToFen("1,200.50"))
        assertEquals(120050L, BillText.amountToFen("1，200.50")) // 全角逗号
        assertEquals(880L, BillText.amountToFen("\u00A08.80"))   // 不换行空格
        assertEquals(8850L, BillText.amountToFen(" 88.50\t"))
    }

    @Test
    fun `金额非法或负或空或零返回 null 或零`() {
        assertNull(BillText.amountToFen("abc"))
        assertNull(BillText.amountToFen(""))
        assertNull(BillText.amountToFen("   "))
        assertNull(BillText.amountToFen("-5"))
        // 金额为 0：toFen 合法返回 0L（非负常量），由调用方判 <=0 计 PARSE_ERROR（PRD「金额>0」口径）。
        assertEquals(0L, BillText.amountToFen("0"))
        assertEquals(0L, BillText.amountToFen("0.00"))
    }

    // ---- description 拼接口径（DEC-015） ----

    @Test
    fun `description 备注非空且非斜杠追加分隔符`() {
        assertEquals("商品说明｜备注", BillText.buildDescription("商品说明", "备注"))
        assertEquals("拿铁", BillText.buildDescription("拿铁", "/"))
        assertEquals("拿铁", BillText.buildDescription("拿铁", ""))
        assertEquals("备注", BillText.buildDescription("", "备注"))
    }

    @Test
    fun `description 超 200 字符截断`() {
        val long = "a".repeat(250)
        assertEquals(200, BillText.buildDescription(long, "").length)
        // 拼接后也截断到 200
        assertEquals(200, BillText.buildDescription("x".repeat(150), "y".repeat(150)).length)
    }

    // ---- 日期归一化（PRD §F-12 字段映射：仅取日期，时分秒丢弃） ----

    @Test
    fun `normalizeDate 兼容多种分隔与单位月日`() {
        assertEquals("2026-08-24", BillText.normalizeDate("2026-08-24 12:34:56"))
        assertEquals("2026-08-24", BillText.normalizeDate("2026/8/24"))
        assertEquals("2026-08-04", BillText.normalizeDate("2026.8.4"))
        assertEquals("2026-08-24", BillText.normalizeDate("2026-08-24"))
    }

    @Test
    fun `normalizeDate 非法或空返回 null`() {
        assertNull(BillText.normalizeDate(""))
        assertNull(BillText.normalizeDate("   "))
        assertNull(BillText.normalizeDate("不是日期"))
        assertNull(BillText.normalizeDate("2026-13-40"))
    }

    // ---- 收/支 映射 ----

    @Test
    fun `mapDirection 支出收入与中性`() {
        assertEquals(TransactionType.EXPENSE, BillText.mapDirection("支出"))
        assertEquals(TransactionType.INCOME, BillText.mapDirection("收入"))
        assertNull(BillText.mapDirection("不计收支"))
        assertNull(BillText.mapDirection("/"))
        assertNull(BillText.mapDirection(""))
    }
}