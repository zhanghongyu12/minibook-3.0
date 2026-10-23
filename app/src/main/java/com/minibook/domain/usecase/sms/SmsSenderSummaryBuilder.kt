package com.minibook.domain.usecase.sms

import com.minibook.domain.model.SmsSenderSummary

/** 扫描期发送方聚合（可变累加器；只在 [SmsBillImportUseCase.scan] 内使用，不落库）。 */
internal class SmsSenderAggregate {
    var totalCount: Int = 0
    var billCount: Int = 0
    var lastAtMs: Long = 0L
    var institution: String? = null
}

/**
 * 「导入来源」清单构造（docs/15 第 2 条，2026-10-13）。
 *
 * 从扫描期的发送方聚合生成来源清单，口径：
 * - `importEnabled = sender !in noImport`（未勾选来源在 UI 上显示为未勾选）；
 * - 账单多的排前面（用户最可能想留的就是它），同数按号码**数值**升序（如 95526 先于 10690000，
 *   两者都非数字时按字典序兜底）。
 *
 * 抽成纯函数是为了可 JVM 单测（`scan()` 本身依赖 Android 短信库与 DataStore）。
 */
internal object SmsSenderSummaryBuilder {

    fun build(
        aggregates: Map<String, SmsSenderAggregate>,
        noImport: Set<String>,
    ): List<SmsSenderSummary> = aggregates.map { (sender, agg) ->
        SmsSenderSummary(
            sender = sender,
            institution = agg.institution,
            totalCount = agg.totalCount,
            billCount = agg.billCount,
            lastAtMs = agg.lastAtMs,
            importEnabled = sender !in noImport,
        )
    }.sortedWith(
        compareByDescending<SmsSenderSummary> { it.billCount }
            .thenBy { it.sender.toLongOrNull() ?: Long.MAX_VALUE }
            .thenBy { it.sender },
    )
}
