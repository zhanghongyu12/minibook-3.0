package com.minibook.data.local.bill

import android.content.Context
import android.net.Uri
import com.minibook.domain.usecase.bill.BillFilePreparer
import com.minibook.domain.usecase.bill.BillSourceInput
import com.minibook.domain.usecase.bill.PreparedBillFile
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.util.UUID

/**
 * SAF（Storage Access Framework）账单文件读取（F-12，DEC-017，ARCHITECTURE §16.6）。
 *
 * 解析栈**唯一**触碰 Android 框架（ContentResolver/Uri）的边界类：把 `uriString`（ViewModel
 * 传入 `android.net.Uri.toString()`，§13.6）落地为 [PreparedBillFile]——
 * - XLSX：拷到 `cacheDir/minibook_import_<uuid>.xlsx` 唯一临时文件（`ZipFile` 需可 seek File），
 *   [PreparedBillFile.close] 删除；
 * - CSV：直接开 GBK [InputStreamReader] 流式读（支付宝「交易流水证明」GBK 编码）。
 *
 * 判型：扩展名主判 + `PK\x03\x04` 魔数兜底（[DefaultBillParserFactory] 同口径）；
 * 嗅探前 4 字节经 `BufferedInputStream.mark/reset` 不丢字。
 */
class SafBillFilePreparer(
    private val context: Context,
) : BillFilePreparer {

    override fun prepare(uriString: String): PreparedBillFile {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: uri.lastPathSegment
        val ext = (displayName?.substringAfterLast('.', missingDelimiterValue = "") ?: "").lowercase()

        val raw = resolver.openInputStream(uri) ?: throw IOException("无法打开所选文件: $uriString")
        val buffered = BufferedInputStream(raw)

        // 嗅探前 4 字节后 reset：CSV 不丢字、XLSX 从 0 起完整拷贝（DEC-017 魔数嗅探）。
        buffered.mark(4)
        val magic = ByteArray(4)
        val read = buffered.read(magic)
        buffered.reset()

        val magicBytes = if (read > 0) magic.copyOf(read) else null
        val isXlsx = ext == XLSX_EXT || isZipMagic(raw = magic, read = read)

        return if (isXlsx) {
            val tempFile = File(context.cacheDir, "$TEMP_XLSX_PREFIX${UUID.randomUUID()}.xlsx")
            buffered.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            XlsxPrepared(tempFile, displayName, magicBytes)
        } else {
            val reader = InputStreamReader(buffered, Charset.forName(GBK_CHARSET))
            CsvPrepared(reader, displayName, magicBytes)
        }
    }

    /** 查 DISPLAY_NAME（如 content:// 的原始文件名）；provider 不支持则返回 null。 */
    private fun queryDisplayName(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): String? = try {
        resolver.query(uri, arrayOf(DISPLAY_NAME_COLUMN), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(DISPLAY_NAME_COLUMN)
            if (index >= 0) cursor.getString(index) else null
        }
    } catch (e: Exception) {
        null
    }

    /** `PK\x03\x04`（ZIP/XLSX）魔数嗅探；read < 4 的短文件不算 zip。 */
    private fun isZipMagic(raw: ByteArray, read: Int): Boolean =
        read >= 4 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte() &&
            raw[2] == 0x03.toByte() && raw[3] == 0x04.toByte()

    private class CsvPrepared(
        private val reader: Reader,
        override val displayName: String?,
        override val magicBytes: ByteArray?,
    ) : PreparedBillFile {
        override val input: BillSourceInput = BillSourceInput.Csv(reader)

        override fun close() {
            reader.close()
        }
    }

    private class XlsxPrepared(
        private val tempFile: File,
        override val displayName: String?,
        override val magicBytes: ByteArray?,
    ) : PreparedBillFile {
        override val input: BillSourceInput = BillSourceInput.Xlsx(tempFile)

        override fun close() {
            tempFile.delete()
        }
    }

    private companion object {
        const val TEMP_XLSX_PREFIX = "minibook_import_"
        const val GBK_CHARSET = "GBK"
        const val XLSX_EXT = "xlsx"

        /** OpenableColumns.DISPLAY_NAME 的稳定值（API 29 起才有的 OpenableColumns，minSdk 26 直采字面量）。 */
        const val DISPLAY_NAME_COLUMN = "_display_name"
    }
}