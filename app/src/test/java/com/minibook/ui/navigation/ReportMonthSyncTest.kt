package com.minibook.ui.navigation

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ReportMonthSync] 最小单测：首页「报表」入口点击时 set 当前选中月，报表侧读取即得该月。 */
class ReportMonthSyncTest {

    @Test
    fun `set 写入当前选中月份`() {
        val month = YearMonth.of(2026, 5)

        ReportMonthSync.set(month)

        assertEquals(month, ReportMonthSync.month.value)
    }
}