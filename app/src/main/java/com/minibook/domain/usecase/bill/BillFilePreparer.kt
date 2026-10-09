package com.minibook.domain.usecase.bill

import java.io.File

/**
 * 账单文件读取（域接口，F-12，API §13.4 / ARCHITECTURE §16.6，DEC-017）。
 *
 * 数据层唯一实现 [com.minibook.data.local.bill.SafBillFilePreparer]（Android 依赖
 * Context/ContentResolver 边界）；XLSX 落 cacheDir 临时文件（`ZipFile` 需可 seek），
 * CSV 直接开 GBK Reader 流式读。返回 [PreparedBillFile] 由调用方 `use {}` 保证清理。
 */
interface BillFilePreparer {
    fun prepare(uriString: String): PreparedBillFile

    /**
     * 已落地的本地文件（F-13 邮件账单导入路径）：判型口径与 [prepare] 完全一致（魔数优先、扩展名兜底），
     * 但**不接管文件生命周期**——[PreparedBillFile.close] **绝不删除** [file]，由调用方负责清理。
     *
     * @param displayName 原始文件名（可为 null；用于解析器判型与展示）
     */
    fun prepareLocalFile(file: File, displayName: String? = null): PreparedBillFile

    /**
     * 申请一个受管临时文件（F-13：下载/解压的中间产物），语义同 `File.createTempFile`
     * 但落在 App cache 目录。**文件由调用方负责删除**（本接口不登记、不自动回收）。
     */
    fun createTempFile(prefix: String, suffix: String): File
}

/**
 * 已准备可解析输入句柄，[AutoCloseable] 保证 finally 清理（DEC-017：关闭流 + 删临时文件）。
 *
 * [magicBytes] 为文件头嗅探字节（≥4 或 null），供
 * [com.minibook.data.datasource.BillParserFactory] 兜底判型。
 */
interface PreparedBillFile : AutoCloseable {
    val input: BillSourceInput
    val displayName: String?
    val magicBytes: ByteArray?
}