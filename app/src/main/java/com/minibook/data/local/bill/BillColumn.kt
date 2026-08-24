package com.minibook.data.local.bill

/**
 * 账单表头列关键（F-12，DEC-016/019，动态表头探测的「列名归一化」）。
 *
 * 支付宝（CSV）与微信（XLSX）共用同一套动态探测：不写死列序/前导行数，只匹配列名关键词。
 * [match] 的判定顺序即优先级（更具体者在前，避免「交易单号/交易订单号」被「金额/单号」等误捕）。
 */
internal enum class BillColumn {
    DATE,
    TYPE,
    AMOUNT,
    COUNTERPARTY,
    PAYMENT_METHOD,
    GOODS,
    REMARK,
    ORDER_NO,
    PLATFORM_CATEGORY,
    STATUS, ;

    companion object {
        /**
         * 表头单元格文本 → 列关键；不命中返回 null。
         *
         * 口径（PRD §F-12 字段映射 + DEC-016 关键词表）：
         * - DATE：含「交易时间」（支付宝「交易创建时间」同含「交易」「时间」，一并命中）；
         * - AMOUNT：含「金额」（覆盖「金额(元)」「金额（元）」半/全角括号）；
         * - PAYMENT_METHOD：含「支付方式」或「付款方式」（覆盖「收付款方式」「收/付款方式」）；
         * - ORDER_NO：含「交易单号/交易订单号」或恰为「交易号」（排除「商户单号/商家订单号」）；
         * - PLATFORM_CATEGORY：含「交易分类/交易类型」或恰为「类型」（支付宝流水证明「类型」列）。
         */
        fun match(raw: String): BillColumn? {
            val h = raw.trim()
            if (h.isEmpty()) return null
            return when {
                h.contains("交易时间") || (h.contains("交易") && h.contains("时间")) -> DATE
                h.contains("收/支") -> TYPE
                h.contains("金额") -> AMOUNT
                h.contains("交易对方") || h.contains("对方") -> COUNTERPARTY
                h.contains("商品") -> GOODS
                h.contains("备注") -> REMARK
                h.contains("支付方式") || h.contains("付款方式") -> PAYMENT_METHOD
                h.contains("交易单号") || h.contains("交易订单号") || h == "交易号" -> ORDER_NO
                h.contains("交易分类") || h.contains("交易类型") || h == "类型" -> PLATFORM_CATEGORY
                h.contains("状态") -> STATUS
                else -> null
            }
        }
    }
}

/**
 * 动态表头探测器（F-12，PRD §F-12 解析规则 1）。
 *
 * [mapColumns] 对一行表头 cells 建「列关键 → 列序」映射；仅当同时命中
 * DATE + TYPE + AMOUNT 三个必需列才判定为表头，否则返回 null（继续扫描后续行，
 * 前导说明行数量变化不影响解析）。
 */
internal object BillHeaderMatcher {

    /** 表头必需列：同时含「交易时间」「收/支」「金额」才算表头（PRD §F-12）。 */
    private val REQUIRED: Set<BillColumn> = setOf(BillColumn.DATE, BillColumn.TYPE, BillColumn.AMOUNT)

    /**
     * @return 命中映射（列关键 → 列序）；未命中必需列则 null。
     */
    fun mapColumns(headers: List<String>): Map<BillColumn, Int>? {
        val map = linkedMapOf<BillColumn, Int>()
        headers.forEachIndexed { index, raw ->
            putIfAbsent(map, BillColumn.match(raw), index)
        }
        return if (REQUIRED.all { it in map }) map else null
    }

    /**
     * XLSX 用：按**绝对列序**（cell 的 `r` 列字母换算）建映射，cell 可能稀疏
     * （空 cell 缺 `<c>`），不能假设按 List 下标连续（DEC-016 #6）。
     *
     * @return 命中映射（列关键 → 绝对列序）；未命中必需列则 null。
     */
    fun mapColumnsByIndex(cells: Map<Int, String>): Map<BillColumn, Int>? {
        val map = linkedMapOf<BillColumn, Int>()
        // 按绝对列序升序遍历，保证「首列优先」对同名关键词列确定。
        cells.entries.sortedBy { it.key }.forEach { (index, raw) ->
            putIfAbsent(map, BillColumn.match(raw), index)
        }
        return if (REQUIRED.all { it in map }) map else null
    }

    /** 同名关键词首列优先：已在映射中的列不覆盖（避免「金额(元)」与潜在「退款金额」并列歧义）。 */
    private fun putIfAbsent(map: MutableMap<BillColumn, Int>, column: BillColumn?, index: Int) {
        if (column != null && column !in map) map[column] = index
    }
}