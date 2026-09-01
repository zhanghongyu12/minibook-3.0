package com.minibook.data.local.bill

import com.minibook.data.datasource.BillParserFactory
import com.minibook.domain.usecase.bill.BillParser

/**
 * [BillParserFactory] 的唯一实现（无状态，契约的默认实现）。
 *
 * 判型细节见 [BillParserFactory] 契约；此处仅承载「唯一实现」：内容嗅探（zip 魔数）优先于
 * 扩展名分发，未命中返回 null → 调用方折为「未识别账单格式」（零落库，DEC-017）。
 */
object DefaultBillParserFactory : BillParserFactory {

    private const val XLSX_EXT = "xlsx"
    private const val CSV_EXT = "csv"

    override fun resolve(displayName: String?, magicBytes: ByteArray?): BillParser? {
        val ext = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?: ""
        return when {
            magicBytes.isZipMagic() -> WechatXlsxParser // 内容嗅探前置：魔数优先于扩展名
            ext == XLSX_EXT -> WechatXlsxParser
            ext == CSV_EXT -> AlipayCsvParser
            ext.isEmpty() -> AlipayCsvParser // 无扩展名且非 zip 魔数 → 按文本（CSV）试解
            else -> null                    // 未知扩展名且非 zip → 未识别
        }
    }

    /** `PK\x03\x04`（ZIP/XLSX 文件头魔数）嗅探（DEC-017）。 */
    private fun ByteArray?.isZipMagic(): Boolean {
        val b = this ?: return false
        return b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() &&
            b[2] == 0x03.toByte() && b[3] == 0x04.toByte()
    }
}