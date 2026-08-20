package com.minibook.util

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TimeFormatter 单测（DATABASE §9.5 时间/日期口径）。
 *
 * 覆盖：纯日期原样返回；ISO 完整时间戳 → yyyy-MM-dd（时区相关，仅断言格式）；
 * YearMonth → yyyy-MM 补零；nowIso 含 T 且以 Z 结尾；parseIso 可回解 nowIso。
 */
class TimeFormatterTest {

    @Test
    fun `formatDate 纯日期原样`() {
        assertEquals("2026-08-18", TimeFormatter.formatDate("2026-08-18"))
    }

    @Test
    fun `formatDate ISO 完整时间戳转本地日`() {
        // 结果依赖系统时区（UTC 时区可能跨日），仅断言 yyyy-MM-dd 格式。
        val result = TimeFormatter.formatDate("2026-08-18T06:14:30.123Z")
        assertTrue(result.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun `formatDate 非法串抛 DateTimeException`() {
        org.junit.Assert.assertThrows(java.time.DateTimeException::class.java) {
            TimeFormatter.formatDate("not-a-date")
        }
    }

    @Test
    fun `formatYearMonth 单月补零`() {
        assertEquals("2026-08", TimeFormatter.formatYearMonth(YearMonth.of(2026, 8)))
        assertEquals("2026-12", TimeFormatter.formatYearMonth(YearMonth.of(2026, 12)))
        assertEquals("2026-01", TimeFormatter.formatYearMonth(YearMonth.of(2026, 1)))
        assertEquals("2026-10", TimeFormatter.formatYearMonth(YearMonth.of(2026, 10)))
    }

    @Test
    fun `nowIso 含 T 且以 Z 结尾`() {
        val now = TimeFormatter.nowIso()
        assertTrue("nowIso 应含 T：$now", now.contains("T"))
        assertTrue("nowIso 应以 Z 结尾：$now", now.endsWith("Z"))
    }

    @Test
    fun `parseIso 解析 nowIso 不抛异常`() {
        TimeFormatter.parseIso(TimeFormatter.nowIso())
    }
}
