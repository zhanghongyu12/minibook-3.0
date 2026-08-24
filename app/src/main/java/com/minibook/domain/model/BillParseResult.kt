package com.minibook.domain.model

/**
 * 账单解析结果与跳过明细（F-12，API §13.3 / ARCHITECTURE §16.3）。
 *
 * - [Success]：解析成功，`rows` 为有效行（待自动分类 + 幂等去重），`skipped` 为
 *   行级过滤/解析失败的逐条明细（NEUTRAL/CLOSED/PARSE_ERROR，非致命）；
 * - [Unrecognized]：未识别格式 / 表头未命中 / 文件损坏，零落库（UseCase 转
 *   [BillImportResult.unrecognized]）。
 */
sealed interface BillParseResult {

    data class Success(
        val rows: List<ParsedBillRow>,
        val skipped: List<ParseSkippedRow>,
    ) : BillParseResult

    data class Unrecognized(val reason: String) : BillParseResult
}

/**
 * 单行跳过明细：行号（物理行号，1 起）+ 原因 + 可读细节（如「金额非法：abc」）。
 * 中文文案由 UI 层映射（DEC-020）；`reason` 为 [ImportSkipReason]。
 */
data class ParseSkippedRow(
    val rowNumber: Int,
    val reason: ImportSkipReason,
    val detail: String,
)