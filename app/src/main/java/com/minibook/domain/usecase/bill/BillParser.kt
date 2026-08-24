package com.minibook.domain.usecase.bill

import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.TransactionSource
import java.io.File
import java.io.Reader

/**
 * 账单解析器抽象（F-12，API §13.4 / ARCHITECTURE §16.3）。
 *
 * 纯 Kotlin（不 import android.*），JVM 可单测；两个实现：
 * - [com.minibook.data.local.bill.AlipayCsvParser]（支付宝 GBK CSV，[BillSourceInput.Csv]）；
 * - [com.minibook.data.local.bill.WechatXlsxParser]（微信 XLSX，[BillSourceInput.Xlsx]）。
 *
 * [parse] 为阻塞同步方法（读 Reader/File），由 ImportBillUseCase 在 IO 调度器上调用。
 * [format] 声明该解析器产出的 [TransactionSource]（连同一批 ParsedBillRow 的平台来源）。
 */
interface BillParser {
    val format: TransactionSource

    fun parse(input: BillSourceInput): BillParseResult
}

/**
 * 可解析输入句柄（DEC-017）：CSV 为顺序读的 GBK [Reader]（支付宝），XLSX 为已落
 * cacheDir、可 seek 的 [File]（微信，`ZipFile` 要求）。
 */
sealed interface BillSourceInput {
    data class Csv(val reader: Reader) : BillSourceInput
    data class Xlsx(val file: File) : BillSourceInput
}