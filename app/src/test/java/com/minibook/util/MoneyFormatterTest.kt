package com.minibook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * MoneyFormatter 单测（TASK-010，DATABASE §9.2/§9.3）。
 *
 * 覆盖：toFen 半舍/常规/整数补零、往返无损、非法输入；formatFen 千分位、
 * 负数、无符号、上限不溢出。
 */
class MoneyFormatterTest {

    // ---- toFen：元 → 分（§9.2 半舍法） ----

    @Test
    fun `toFen 半舍进位`() {
        assertEquals(1235L, MoneyFormatter.toFen("12.345"))
        assertEquals(1235L, MoneyFormatter.toFen("12.3451"))
        assertEquals(1L, MoneyFormatter.toFen("0.005"))
    }

    @Test
    fun `toFen 常规两位小数`() {
        assertEquals(1234L, MoneyFormatter.toFen("12.34"))
    }

    @Test
    fun `toFen 整数元补零`() {
        assertEquals(1200L, MoneyFormatter.toFen("12"))
        assertEquals(0L, MoneyFormatter.toFen("0"))
        assertEquals(1L, MoneyFormatter.toFen("0.01"))
    }

    @Test
    fun `toFen 往返无损`() {
        assertEquals("¥12.34", MoneyFormatter.formatFen(MoneyFormatter.toFen("12.34")))
        assertEquals("¥0.01", MoneyFormatter.formatFen(MoneyFormatter.toFen("0.01")))
        assertEquals("¥99,999,999.99", MoneyFormatter.formatFen(MoneyFormatter.toFen("99999999.99")))
    }

    @Test
    fun `toFen 非法输入抛 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { MoneyFormatter.toFen("") }
        assertThrows(IllegalArgumentException::class.java) { MoneyFormatter.toFen("   ") }
        assertThrows(IllegalArgumentException::class.java) { MoneyFormatter.toFen("abc") }
        assertThrows(IllegalArgumentException::class.java) { MoneyFormatter.toFen("12a") }
        assertThrows(IllegalArgumentException::class.java) { MoneyFormatter.toFen("-12.34") }
        assertThrows(IllegalArgumentException::class.java) { MoneyFormatter.toFen("-0.01") }
    }

    @Test
    fun `toFen 超长输入溢出抛 IllegalArgument`() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.toFen("99999999999999999999")
        }
    }

    // ---- formatFen：分 → 元展示（§9.3 整数拆分） ----

    @Test
    fun `formatFen 千分位与两位小数`() {
        assertEquals("¥1,234.56", MoneyFormatter.formatFen(123456L))
        assertEquals("¥12,345.67", MoneyFormatter.formatFen(1234567L))
        assertEquals("¥0.00", MoneyFormatter.formatFen(0L))
        assertEquals("¥0.05", MoneyFormatter.formatFen(5L))
    }

    @Test
    fun `formatFen 负数`() {
        assertEquals("¥-0.50", MoneyFormatter.formatFen(-50L))
        assertEquals("¥-1.50", MoneyFormatter.formatFen(-150L))
        assertEquals("¥-1,234.56", MoneyFormatter.formatFen(-123456L))
    }

    @Test
    fun `formatFen 无符号`() {
        assertEquals("1,234.56", MoneyFormatter.formatFen(123456L, withSymbol = false))
        assertEquals("-0.50", MoneyFormatter.formatFen(-50L, withSymbol = false))
    }

    @Test
    fun `formatFen 上限不溢出`() {
        // 99_999_999_99 分 ≈ 99,999,999.99 元（DATABASE §9.4 上限）
        assertEquals("¥99,999,999.99", MoneyFormatter.formatFen(99_999_999_99L))
    }
}
