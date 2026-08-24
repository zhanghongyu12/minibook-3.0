package com.minibook.domain.model

/**
 * 账单导入结果聚合（F-12，API §13.3 / ARCHITECTURE §16.9，DEC-015/019 口径）。
 *
 * 「逐行过滤 + 单事务整批写入」的结果反馈：
 * - [success]：实际新增行数；
 * - [existing]：已存在（幂等去重：既有 externalId 相交 + 文件内重复 + IGNORE 兜底，DEC-019）；
 * - [neutral]：不计收支/「/」/空（NEUTRAL）跳过数；
 * - [closed]：支付宝「交易关闭」跳过数；
 * - [parseErrors]：解析失败逐条明细（行号+原因，PARSE_ERROR）；
 * - [unrecognized]：未识别格式（零落库）标记。
 *
 * UI（TASK-044）据 [success] 是否为 0 判断「本次无新增」；中文文案由 UI 层映射（DEC-020）。
 */
data class BillImportResult(
    val success: Int = 0,
    val existing: Int = 0,
    val neutral: Int = 0,
    val closed: Int = 0,
    val parseErrors: List<ParseSkippedRow> = emptyList(),
    val unrecognized: Boolean = false,
)