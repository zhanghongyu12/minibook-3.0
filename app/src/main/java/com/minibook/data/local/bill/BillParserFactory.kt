package com.minibook.data.local.bill

import com.minibook.domain.usecase.bill.BillParser

/**
 * 账单解析器工厂（F-12，API §13.4 / ARCHITECTURE §16.2）。
 *
 * 契约放 data/local/bill（与 DEC-017 目录归属一致；ImportBillUseCase 域侧经本接口获得解析器，
 * 与域依赖 `data.datasource.TransactionRunner` 纯接口同径）。
 *
 * [resolve] 判型：**内容嗅探主判**——先 `PK\x03\x04` 魔数嗅探，命中即按 `WechatXlsxParser`
 * （XLSX）；否则再按扩展名分发（`.xlsx`→微信、`.csv`→支付宝、无扩展名→按 CSV 试解）。
 * 修复「.csv 名但内容是 zip」被扩展名误分发到 CSV 的问题（DEC-017「扩展名失配→内容嗅探兜底」）。
 * CSV 自身表头探测失败 → `parse` 返回 `Unrecognized`，同样满足「探测失败 → 零落库」。
 * 判不出 → null → 「未识别账单格式」。
 */
interface BillParserFactory {
    fun resolve(displayName: String?, magicBytes: ByteArray?): BillParser?
}

/**
 * 唯一实现（无状态，[BillParserFactory] 契约的默认实现）。
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