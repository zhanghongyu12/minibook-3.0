package com.minibook.domain.usecase.bill.zip

import java.io.File
import javax.inject.Inject

/**
 * zip 加密类型判定结果。
 */
sealed interface ZipEncryptionInfo {
    /** ZipCrypto（传统 PKWARE 加密），可用 6 位纯数字口令穷举。 */
    data object ZipCrypto : ZipEncryptionInfo

    /** 不支持穷举/解密的情形：WinZip AES（method=99 或 extra field 0x9901）、未加密、未知类型或文件非法。 */
    data class Unsupported(val reason: String) : ZipEncryptionInfo
}

/**
 * 读取 zip 中央目录第 0 个条目，判定加密类型。
 *
 * 判定口径（已验证）：
 * - 条目 method `0`/`8` 且 flag bit0(0x0001) 置位 → [ZipEncryptionInfo.ZipCrypto]；
 * - `method == 99` 或中央目录/本地头 extra field 含 header id `0x9901` → WinZip AES → Unsupported；
 * - flag bit0 未置位（未加密）→ Unsupported；
 * - 文件不存在 / 中央目录非法 → Unsupported。
 */
class ZipArchiveInspector @Inject constructor() {

    /** 读中央目录第 0 个条目，判定加密类型。文件不存在/非法 → 返回 [ZipEncryptionInfo.Unsupported]。 */
    fun inspect(zipFile: File): ZipEncryptionInfo {
        val entry = ZipStructureReader.readEntry0(zipFile)
            ?: return ZipEncryptionInfo.Unsupported("文件不存在或 zip 中央目录非法")
        if (entry.isAes) {
            return ZipEncryptionInfo.Unsupported("WinZip AES 加密（method=99 或 extra field 0x9901）暂不支持")
        }
        if (!entry.isEncrypted) {
            return ZipEncryptionInfo.Unsupported("条目未加密")
        }
        return if (entry.method == 0 || entry.method == 8) {
            ZipEncryptionInfo.ZipCrypto
        } else {
            ZipEncryptionInfo.Unsupported("不支持的压缩方法 ${entry.method}")
        }
    }
}

/**
 * 中央目录第 0 个条目的关键字段（little-endian 已解码）。供同包 Cracker / Extractor 复用。
 */
internal class ZipEntryInfo(
    val method: Int,
    val flag: Int,
    val modTime: Int,
    val modDate: Int,
    /** 原始（未压缩）内容 CRC32，无符号 32 位值。 */
    val crc32: Long,
    /** 含 12 字节加密头的压缩后长度。 */
    val packedSize: Long,
    val unpackedSize: Long,
    val localHeaderOffset: Long,
    /** 本地头数据起点（密文起点，含加密头）。 */
    val localDataOffset: Long,
    val nameBytes: ByteArray,
    val extraBytes: ByteArray,
    /** 是否为 WinZip AES（method=99 或中央目录/本地头 extra field 含 0x9901）。 */
    val isAes: Boolean,
) {
    /** 是否加密（general purpose flag bit 0）。 */
    val isEncrypted: Boolean get() = (flag and 0x0001) != 0

    /** 是否带 data descriptor（general purpose flag bit 3）。 */
    val isDataDescriptor: Boolean get() = (flag and 0x0008) != 0

    /** 是否可用口令穷举的 ZipCrypto。 */
    val isZipCrypto: Boolean get() = isEncrypted && (method == 0 || method == 8) && !isAes
}

/** 扫描 extra field，是否存在 header id 0x9901（WinZip AES 标志）。 */
private fun containsAesExtra(extra: ByteArray): Boolean {
    var p = 0
    while (p + 4 <= extra.size) {
        val id = (extra[p].toInt() and 0xFF) or ((extra[p + 1].toInt() and 0xFF) shl 8)
        val size = (extra[p + 2].toInt() and 0xFF) or ((extra[p + 3].toInt() and 0xFF) shl 8)
        if (id == 0x9901) return true
        p += 4 + size
    }
    return false
}

/**
 * zip 结构解析（EOCD → 中央目录 → 本地头），全部手写 little-endian，不依赖 `java.util.zip`。
 *
 * 仅解析第 0 个条目（本场景账单包为单条目）；文件名存在/非法统一返回 null，由调用方转成 Unsupported。
 */
internal object ZipStructureReader {

    private const val SIG_EOCD = 0x06054b50
    private const val SIG_CENTRAL = 0x02014b50
    private const val SIG_LOCAL = 0x04034b50

    /** 从文件读取并解析第 0 个中央目录条目（含本地头数据起点）。文件不存在/非法 → null。 */
    fun readEntry0(file: File): ZipEntryInfo? = try {
        parseEntry0(file.readBytes())
    } catch (e: Exception) {
        null
    }

    /** 从已读入的字节数组解析第 0 个条目（供 Cracker/Extractor 复用，避免二次读文件）。 */
    fun parseEntry0(bytes: ByteArray): ZipEntryInfo? {
        if (bytes.size < 22) return null
        val cdOffset = findEocd(bytes) ?: return null
        return parseCentralEntry(bytes, cdOffset)
    }

    /** 从文件尾倒查 EOCD 签名 `0x06054b50`，返回中央目录偏移；找不到返回 null。 */
    private fun findEocd(bytes: ByteArray): Int? {
        // EOCD 固定 22 字节 + 注释最长 0xFFFF 字节；从末尾向下找最后一个签名。
        val searchStart = (bytes.size - (22 + 0xFFFF)).coerceAtLeast(0)
        for (i in bytes.size - 22 downTo searchStart) {
            if (readU32(bytes, i) == SIG_EOCD.toLong()) {
                val cdOffset = readU32(bytes, i + 16)
                if (cdOffset >= 0 && cdOffset < bytes.size) return cdOffset.toInt()
                return null
            }
        }
        return null
    }

    /** 解析中央目录第 0 个条目（签名 0x02014b50，各字段偏移见 KDoc）。 */
    private fun parseCentralEntry(bytes: ByteArray, cdOffset: Int): ZipEntryInfo? {
        if (cdOffset < 0 || cdOffset + 46 > bytes.size) return null
        if (readU32(bytes, cdOffset) != SIG_CENTRAL.toLong()) return null
        val flag = readU16(bytes, cdOffset + 8)
        val method = readU16(bytes, cdOffset + 10)
        val modTime = readU16(bytes, cdOffset + 12)
        val modDate = readU16(bytes, cdOffset + 14)
        val crc = readU32(bytes, cdOffset + 16)
        val packed = readU32(bytes, cdOffset + 20)
        val unpacked = readU32(bytes, cdOffset + 24)
        val nameLen = readU16(bytes, cdOffset + 28)
        val extraLen = readU16(bytes, cdOffset + 30)
        val localOffset = readU32(bytes, cdOffset + 42)
        val nameStart = cdOffset + 46
        if (nameStart + nameLen + extraLen > bytes.size) return null
        val nameBytes = bytes.copyOfRange(nameStart, nameStart + nameLen)
        val extraBytes = bytes.copyOfRange(nameStart + nameLen, nameStart + nameLen + extraLen)
        val dataOffset = readLocalDataOffset(bytes, localOffset) ?: return null
        val aes = method == 99 ||
            containsAesExtra(extraBytes) ||
            localHeaderHasAesExtra(bytes, localOffset)
        return ZipEntryInfo(
            method = method,
            flag = flag,
            modTime = modTime,
            modDate = modDate,
            crc32 = crc,
            packedSize = packed,
            unpackedSize = unpacked,
            localHeaderOffset = localOffset,
            localDataOffset = dataOffset,
            nameBytes = nameBytes,
            extraBytes = extraBytes,
            isAes = aes,
        )
    }

    /** 本地头（签名 0x04034b50）：nameLen@26、extraLen@28；数据起点 = 本地头偏移 + 30 + nameLen + extraLen。 */
    private fun readLocalDataOffset(bytes: ByteArray, localOffset: Long): Long? {
        val off = localOffset.toInt()
        if (off < 0 || off + 30 > bytes.size) return null
        if (readU32(bytes, off) != SIG_LOCAL.toLong()) return null
        val nameLen = readU16(bytes, off + 26)
        val extraLen = readU16(bytes, off + 28)
        return off.toLong() + 30 + nameLen + extraLen
    }

    /** 本地头 extra field 是否含 header id 0x9901（WinZip AES 标志）。 */
    private fun localHeaderHasAesExtra(bytes: ByteArray, localOffset: Long): Boolean {
        val off = localOffset.toInt()
        if (off < 0 || off + 30 > bytes.size) return false
        if (readU32(bytes, off) != SIG_LOCAL.toLong()) return false
        val nameLen = readU16(bytes, off + 26)
        val extraLen = readU16(bytes, off + 28)
        val extraStart = off + 30 + nameLen
        if (extraStart + extraLen > bytes.size) return false
        return containsAesExtra(bytes.copyOfRange(extraStart, extraStart + extraLen))
    }

    private fun readU16(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

    private fun readU32(bytes: ByteArray, off: Int): Long =
        (bytes[off].toLong() and 0xFF) or
            ((bytes[off + 1].toLong() and 0xFF) shl 8) or
            ((bytes[off + 2].toLong() and 0xFF) shl 16) or
            ((bytes[off + 3].toLong() and 0xFF) shl 24)
}
