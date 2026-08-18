package com.minibook.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * 金额（元 ↔ 分）转换门面，对齐 DATABASE §9。
 *
 * 存储一律「分」Long（§9.1）；展示一律整数拆分，**不经 Float/Double**（§9.3），
 * 杜绝浮点中间态（`12.34 元 → 1234 分`）。业务代码禁止手写 `* 100` / `/ 100f`
 * （代码审查点），统一走本门面。
 */
object MoneyFormatter {

    /**
     * 元（字符串，如 `"12.34"`）→ 分（Long）。
     *
     * DATABASE §9.2 推荐法：先 `setScale(2, HALF_UP)` 固定两位小数（半舍），再
     * `movePointRight(2)` 整体右移两位、`longValueExact()` 精确取整。全程
     * BigDecimal 整数语义，无浮点参与（`"12.345" → 1235`、`"12.34" → 1234`）。
     *
     * @throws IllegalArgumentException 输入为空 / 非数字 / 负数 / 超出 Long 范围。
     */
    fun toFen(input: String): Long {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "金额不能为空" }
        require(!trimmed.startsWith("-")) { "金额不能为负" }
        return try {
            BigDecimal(trimmed)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        } catch (e: ArithmeticException) {
            // longValueExact() 对超 Long 范围输入抛 ArithmeticException，统一转 IllegalArgument
            throw IllegalArgumentException("金额超出可表示范围", e)
        }
    }

    /**
     * 分（Long）→ 元字符串，如 `123456` → `¥1,234.56`。
     *
     * 整数拆分：`yuan = fen / 100`、`cent = fen % 100`，千分位仅对整数部分分组
     * （[DecimalFormat]），小数固定 2 位；全程无 Float/Double 中间态（§9.3）。
     * 负数取绝对值拆分后前置 `-` 号（如 `-50` → `¥-0.50`）。
     *
     * @param withSymbol true 带 `¥` 前缀；false 仅数字（如 `1,234.56`）。
     */
    fun formatFen(fen: Long, withSymbol: Boolean = true): String {
        val sign = if (fen < 0) "-" else ""
        val yuan = (fen / 100).absoluteValue
        val cent = (fen % 100).absoluteValue
        val body = "${groupThousands(yuan)}.${cent.toString().padStart(2, '0')}"
        return (if (withSymbol) "¥" else "") + sign + body
    }

    /** 整数千分位分组（`#,###`）；固定 `Locale.US` 保证分隔符为逗号、输出确定。 */
    private fun groupThousands(value: Long): String =
        DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US)).format(value)
}
