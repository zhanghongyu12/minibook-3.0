package com.minibook.data.local.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 动态表头探测边界单测（F-12，PRD §F-12 解析规则 1 / TASK-041/042 验收「不写死列序/前导行数」）。
 *
 * 补齐 Original 未覆盖的边界：
 * - 必需列三缺一（无「金额」/无「收/支」/无「交易时间」）不判定表头（前导行继续扫描）；
 * - 列序重排仍按关键词命中（「换列仍命中」验收）；
 * - 「对方账号」「商户单号/商家订单号」等 PRD 明列「不落库」字段不被误捕。
 */
class BillHeaderMatcherTest {

    @Test
    fun `必需列三缺一不判定表头`() {
        // 无金额
        assertNull(BillHeaderMatcher.mapColumns(listOf("交易时间", "收/支", "交易对方")))
        // 无收/支
        assertNull(BillHeaderMatcher.mapColumns(listOf("交易时间", "金额", "交易对方")))
        // 无交易时间
        assertNull(BillHeaderMatcher.mapColumns(listOf("金额", "收/支", "交易对方")))
    }

    @Test
    fun `列序重排仍命中必需列`() {
        // 支付宝真实表头乱序：金额/收/支/交易时间 换位仍命中，且列索引随关键词。
        val map = BillHeaderMatcher.mapColumns(
            listOf("交易对方", "金额(元)", "交易时间", "收/付款方式", "收/支", "交易订单号"),
        )
        assertEquals(mapOf(
            BillColumn.COUNTERPARTY to 0,
            BillColumn.AMOUNT to 1,
            BillColumn.DATE to 2,
            BillColumn.PAYMENT_METHOD to 3,
            BillColumn.TYPE to 4,
            BillColumn.ORDER_NO to 5,
        ), map)
    }

    @Test
    fun `不落库字段不被误捕为订单号与对方`() {
        // 支付宝真实表头：交易对方(2) 在 对方账号(3) 之前 → COUNTERPARTY 取 2；商家订单号不落为 ORDER_NO。
        val map = BillHeaderMatcher.mapColumns(
            listOf("交易时间", "交易分类", "交易对方", "对方账号", "商品说明", "收/支",
                "金额", "收/付款方式", "交易状态", "交易订单号", "商家订单号", "备注"),
        )
        assertEquals(0, map?.get(BillColumn.DATE))
        assertEquals(1, map?.get(BillColumn.PLATFORM_CATEGORY))
        assertEquals(2, map?.get(BillColumn.COUNTERPARTY)) // 交易对方而非对方账号
        assertEquals(4, map?.get(BillColumn.GOODS))
        assertEquals(5, map?.get(BillColumn.TYPE))
        assertEquals(6, map?.get(BillColumn.AMOUNT))
        assertEquals(7, map?.get(BillColumn.PAYMENT_METHOD))
        assertEquals(8, map?.get(BillColumn.STATUS))
        assertEquals(9, map?.get(BillColumn.ORDER_NO)) // 交易订单号而非商家订单号
        assertEquals(11, map?.get(BillColumn.REMARK))
        // 「对方账号」(列 3) 与「商家订单号」(列 10) 均为 PRD 明列「不落库」字段，不映射任何列键。
        assertNull(map?.entries?.firstOrNull { it.value == 3 })
        assertNull(map?.entries?.firstOrNull { it.value == 10 })
        // P3-1：COUNTERPARTY 精确匹配「交易对方」；「对方账号」单独出现也不命中任何列键（不再误捕）。
        assertEquals(BillColumn.COUNTERPARTY, BillColumn.match("交易对方"))
        assertNull(BillColumn.match("对方账号"))
    }

    @Test
    fun `按绝对列序的 XLSX 表头换列命中`() {
        // 微信表头乱序（稀疏 cell：列 0/1/3/4 有值，列 2 空缺）。
        val cells = linkedMapOf<Int, String>(
            0 to "交易单号",
            1 to "金额(元)",
            3 to "交易时间",
            4 to "收/支",
        )
        val map = BillHeaderMatcher.mapColumnsByIndex(cells)
        assertEquals(mapOf(
            BillColumn.ORDER_NO to 0,
            BillColumn.AMOUNT to 1,
            BillColumn.DATE to 3,
            BillColumn.TYPE to 4,
        ), map)
    }
}