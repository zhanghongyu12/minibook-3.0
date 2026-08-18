package com.minibook.util

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间/日期格式化门面，对齐 DATABASE §9.5。
 *
 * 存储约定：`date`（发生日期）为 `yyyy-MM-dd` 本地日；`createdAt/updatedAt/timestamp`
 * 为 ISO-8601 完整时间戳（UTC，审计用）；`month`（预算）为 `yyyy-MM`。
 * 展示统一走本门面（minSdk 26 起 `java.time.*` 无需 desugaring），不重复造轮子。
 */
object TimeFormatter {

    /** `yyyy-MM-dd`：发生日期展示格式（DATABASE §9.5）。 */
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** `yyyy-MM`：月份口径（与 `substr(date,1,7)`、预算 month 一致）。 */
    private val YEAR_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    /**
     * ISO 字符串 → `yyyy-MM-dd` 本地化展示。
     *
     * - 纯日期 `"2026-08-18"`（本地日）原样返回；
     * - 完整时间戳（UTC，如 `createdAt`）先转系统时区本地日再截取日期。
     *
     * @throws DateTimeException 两种格式均无法解析时。
     */
    fun formatDate(iso: String): String {
        val date = try {
            LocalDate.parse(iso)
        } catch (e: DateTimeException) {
            Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return date.format(DATE_FORMATTER)
    }

    /** 年月 → `yyyy-MM`（如 `2026-08`），对齐预算 month 存储口径（§9.5）。 */
    fun formatYearMonth(yearMonth: YearMonth): String = yearMonth.format(YEAR_MONTH_FORMATTER)

    /** 当前 UTC 时间戳（ISO-8601，如 `2026-08-18T06:14:30.123Z`），用于 createdAt/updatedAt。 */
    fun nowIso(): String = Instant.now().toString()

    /** 解析 ISO-8601 时间戳 → [Instant]（用于查询/比较）。 */
    fun parseIso(iso: String): Instant = Instant.parse(iso)
}
