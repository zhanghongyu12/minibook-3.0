package com.minibook.domain.usecase.bill.zip

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.Inflater
import javax.inject.Inject

/**
 * 用已知口令解密并解出 zip 第 0 个条目。
 *
 * 纯 Kotlin、不 import `android.*`，JVM 可单测。流程：
 * 1. 解析中央目录第 0 个条目与本地头数据起点；
 * 2. 整包解密（12 字节加密头 + 压缩数据，密钥连续推进）；
 * 3. 校验字节宽松校验（CRC 高字节 **或** DOS 时间高字节任一，防 data descriptor 场景误杀）；
 * 4. `method=0` 直接取原文、`method=8` 用 [Inflater]（nowrap）解 raw deflate；
 * 5. 按条目名（UTF-8 / GBK / CP437 兜底）写入 destDir，返回解出的文件。
 */
class EncryptedZipExtractor @Inject constructor() {

    /** 用已知口令解密并解出第 0 个条目到 destDir，返回解出的文件（文件名为条目名，UTF-8/CP437 解码）。 */
    fun extract(zipFile: File, password: String, destDir: File): File {
        val bytes = zipFile.readBytes()
        val entry = ZipStructureReader.parseEntry0(bytes)
            ?: throw IllegalArgumentException("无法解析 zip 中央目录：${zipFile.absolutePath}")
        if (!entry.isZipCrypto) {
            throw IllegalArgumentException(
                "条目非 ZipCrypto（method=${entry.method}, flag=0x${entry.flag.toString(16)}），无法用口令解密",
            )
        }

        val start = entry.localDataOffset
        val end = start + entry.packedSize
        if (start < 0 || end > bytes.size || entry.packedSize < 12) {
            throw IllegalArgumentException("条目密文数据越界")
        }
        val cipher = bytes.copyOfRange(start.toInt(), end.toInt())

        val plain = decryptAll(cipher, password)

        // 校验字节（下标 11）宽松校验：CRC 高字节或 DOS 修改时间高字节任一命中即通过。
        val check = plain[11].toInt() and 0xFF
        val expectCrc = ((entry.crc32 ushr 24) and 0xFF).toInt()
        val expectTime = (entry.modTime ushr 8) and 0xFF
        if (check != expectCrc && check != expectTime) {
            throw IllegalArgumentException("口令错误或文件损坏（校验字节不匹配）")
        }

        val compressed = plain.copyOfRange(12, plain.size)
        val content = decompressZipData(entry.method, compressed, entry.unpackedSize)
        val name = decodeEntryName(entry.nameBytes, entry.flag)
        val outFile = File(destDir, safeBaseName(name))
        outFile.parentFile?.mkdirs()
        outFile.writeBytes(content)
        return outFile
    }

    private fun decryptAll(cipher: ByteArray, password: String): ByteArray {
        val state = ZipCrypto.derive(password)
        return ByteArray(cipher.size) { i ->
            ZipCrypto.decryptByte(state, cipher[i].toInt() and 0xFF).toByte()
        }
    }
}

/** 按压缩方法解出未压缩内容：`0`=stored、`8`=raw deflate。 */
internal fun decompressZipData(method: Int, compressed: ByteArray, unpackedSize: Long): ByteArray = when (method) {
    0 -> compressed
    8 -> inflateRaw(compressed, unpackedSize)
    else -> throw IllegalArgumentException("不支持的压缩方法 $method")
}

/** 用 [Inflater]（nowrap=true）解 raw deflate。 */
internal fun inflateRaw(compressed: ByteArray, expectedSize: Long): ByteArray {
    val inflater = Inflater(true)
    return try {
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream(
            expectedSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1024),
        )
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n > 0) {
                out.write(buf, 0, n)
            } else if (inflater.finished() || inflater.needsInput()) {
                break
            } else if (inflater.needsDictionary()) {
                throw IllegalArgumentException("raw deflate 需要预设字典，无法解压")
            } else {
                break // 防御：无进展且无更多输入，避免死循环
            }
        }
        out.toByteArray()
    } finally {
        inflater.end()
    }
}

/**
 * 解码条目名：flag bit 11(0x0800) 置位 → UTF-8；否则 GBK（无损往返校验）→ CP437 兜底。
 *
 * 本场景账单包文件名含中文且多为 GBK 编码，故优先 GBK；GBK 解码结果与原字节无损往返才采信，
 * 否则（如 CP437 高位字节名）回退 CP437。字符集按需解析并逐级兜底，兼容缺 GBK/CP437 的 Android 实现。
 */
internal fun decodeEntryName(nameBytes: ByteArray, flag: Int): String {
    if ((flag and 0x0800) != 0) return String(nameBytes, Charsets.UTF_8)
    return try {
        val gbk = Charset.forName("GBK")
        val decoded = String(nameBytes, gbk)
        if (nameBytes.contentEquals(decoded.toByteArray(gbk))) decoded else decodeCp437(nameBytes)
    } catch (e: Exception) {
        decodeCp437(nameBytes)
    }
}

private fun decodeCp437(nameBytes: ByteArray): String = try {
    String(nameBytes, Charset.forName("Cp437"))
} catch (e: Exception) {
    // 缺 CP437 时按单字节映射兜底，保证 ASCII 可读。
    String(nameBytes, Charsets.ISO_8859_1)
}

/** 取条目名的最后一段（基名），防路径穿越；空名兜底。 */
internal fun safeBaseName(name: String): String =
    name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "extracted.bin" }
