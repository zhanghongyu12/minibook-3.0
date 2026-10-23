package com.minibook.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NetBalanceTrend.from] 纯函数单测（TASK-081 首页 Hero 卡环比行）。
 *
 * 口径：只给**可核对的绝对差额**，不给百分比（上月基准为 0 时百分比无定义）；
 * 上月无记录时 [NetBalanceTrend.hasPrevious] = false、差额归零，由 UI 写「上月无记录」。
 */
class NetBalanceTrendTest {

    @Test
    fun `上月无记录 - 不给差额不编对比`() {
        val trend = NetBalanceTrend.from(currentBalanceFen = 1234L, previousBalanceFen = null)
        assertFalse(trend.hasPrevious)
        assertEquals(0L, trend.deltaFen)
    }

    @Test
    fun `较上月多 - 差额为正`() {
        val trend = NetBalanceTrend.from(currentBalanceFen = 5000L, previousBalanceFen = 3000L)
        assertTrue(trend.hasPrevious)
        assertEquals(2000L, trend.deltaFen)
    }

    @Test
    fun `较上月少 - 差额为负`() {
        val trend = NetBalanceTrend.from(currentBalanceFen = 3000L, previousBalanceFen = 5000L)
        assertTrue(trend.hasPrevious)
        assertEquals(-2000L, trend.deltaFen)
    }

    @Test
    fun `两个月都为负结余 - 差额按真实方向`() {
        // -100 − (-400) = +300：仍是「比上月多」，方向不比绝对值骗人。
        val trend = NetBalanceTrend.from(currentBalanceFen = -100L, previousBalanceFen = -400L)
        assertTrue(trend.hasPrevious)
        assertEquals(300L, trend.deltaFen)
    }

    @Test
    fun `与上月持平 - 差额为零`() {
        val trend = NetBalanceTrend.from(currentBalanceFen = -100L, previousBalanceFen = -100L)
        assertTrue(trend.hasPrevious)
        assertEquals(0L, trend.deltaFen)
    }
}
