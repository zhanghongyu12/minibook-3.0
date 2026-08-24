package com.minibook.data.local.bill

import com.minibook.domain.model.TransactionType
import com.minibook.util.MoneyFormatter
import java.time.LocalDate

/**
 * 账单解析共享的纯文本/数值工具（F-12），供 CSV/XLSX 双解析器复用，避免重复且保口径一致。
 *
 * 金额全程「分」整数：`strip` 后交 [MoneyFormatter.toFen]（BigDecimal，禁 float/double）；
 * 日期归一化为 `yyyy-MM-dd`（时分秒丢弃）；description 拼接 + 截断口径见 [buildDescription]。
 */
internal object BillText {

    /** description 最大长度（DATABASE §4.1 ≤200 字符，与 Validators.MAX_NOTE_LENGTH 同口径）。 */
    const val MAX_DESCRIPTION_LENGTH: Int = 200

    /**
     * 金额字符串清洗（PRD §F-12）：剥掉 `￥/¥/,/空白`（含全角逗号「，」与不换行空格）后
     * 交 [MoneyFormatter.toFen]。任何非法/负/空/超 Long → 返回 null（调用方计 PARSE_ERROR）。
     *
     * @return 金额（分，>0 由调用方再判）；解析失败返回 null。
     */
    fun amountToFen(raw: String): Long? = try {
        MoneyFormatter.toFen(stripAmount(raw))
    } catch (e: IllegalArgumentException) {
        null
    }

    /** 清洗金额字面量：去货币符/千分逗号/各种空白，保留数字与小数点。 */
    private fun stripAmount(raw: String): String = buildString {
        raw.forEach { c ->
            when (c) {
                '￥', '¥', ',', '，', ' ', '\t', '\r', '\n', '\u00A0' -> Unit
                else -> append(c)
            }
        }
    }

    /**
     * 交易时间 → `yyyy-MM-dd`（时分秒丢弃，PRD §F-12）。
     *
     * 兼容 `2026-08-24 12:34:56`（取空格前日期）、`2026/8/24`、`2026.8.24`、`2026-08-24`
     * 等；月/日允许 1~2 位（`LocalDate.of` 补零）。解析失败/空 → null（计 PARSE_ERROR「时间缺失」）。
     */
    fun normalizeDate(raw: String): String? {
        val s = raw.trim().substringBefore(' ').trim()
        if (s.isEmpty()) return null
        val normalized = s.replace('/', '-').replace('／', '-').replace('.', '-')
        return try {
            val parts = normalized.split('-')
            if (parts.size != 3) return null
            LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * description 拼接口径（DEC-015）：商品说明为主，账单备注非空且 ≠「/」时以 `｜` 追加；
     * 拼接后超 [MAX_DESCRIPTION_LENGTH] 截断。入参非空（解析器已 trim）。
     */
    fun buildDescription(goods: String, remark: String): String {
        val base = when {
            remark.isEmpty() || remark == "/" -> goods
            goods.isEmpty() -> remark
            else -> "$goods｜$remark"
        }
        return if (base.length > MAX_DESCRIPTION_LENGTH) base.substring(0, MAX_DESCRIPTION_LENGTH) else base
    }

    /**
     * 收/支 → 领域枚举；「支出」→EXPENSE、「收入」→INCOME，其余（不计收支/「/」/空/中性）→ null
     * （调用方计 NEUTRAL 跳过，PRD §F-12 过滤规则）。
     */
    fun mapDirection(raw: String): TransactionType? = when (raw.trim()) {
        "支出" -> TransactionType.EXPENSE
        "收入" -> TransactionType.INCOME
        else -> null
    }
}