package com.minibook.domain.usecase.bill

/**
 * 账单文件读取（域接口，F-12，API §13.4 / ARCHITECTURE §16.6，DEC-017）。
 *
 * 数据层唯一实现 [com.minibook.data.local.bill.SafBillFilePreparer]（Android 依赖
 * Context/ContentResolver 边界）；XLSX 落 cacheDir 临时文件（`ZipFile` 需可 seek），
 * CSV 直接开 GBK Reader 流式读。返回 [PreparedBillFile] 由调用方 `use {}` 保证清理。
 */
interface BillFilePreparer {
    fun prepare(uriString: String): PreparedBillFile
}

/**
 * 已准备可解析输入句柄，[AutoCloseable] 保证 finally 清理（DEC-017：关闭流 + 删临时文件）。
 *
 * [magicBytes] 为文件头嗅探字节（≥4 或 null），供 [BillParserFactory] 兜底判型。
 */
interface PreparedBillFile : AutoCloseable {
    val input: BillSourceInput
    val displayName: String?
    val magicBytes: ByteArray?
}