package com.minibook.domain.usecase.bill.zip

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** ZipCrypto 加密头长度（12 字节，最后 1 字节为校验字节）。文件级常量，类内外顶层函数共用。 */
private const val HEADER_LEN = 12

/**
 * 口令穷举器：对 ZipCrypto 加密的账单包穷举口令（默认 6 位纯数字，10^6 候选）。
 *
 * 正确性关键（已验证口径，两段式判定）：
 * 1. **快筛**（每个候选都做）：候选口令派生密钥 → 只解密 12 字节加密头 → 取下标 11 的校验字节，
 *    **宽松接受**：等于「中央目录 CRC32 高字节」**或**「DOS 修改时间高字节」任一即通过。绝不同时要求两者
 *    （带 data descriptor 时校验字节约定会变，同时要求会误杀正确口令）。
 * 2. **权威判定**（仅快筛命中者执行）：整包解密 → [Inflater] 解压 → 对**解压后的原始内容**算 CRC32，
 *    与中央目录 CRC 比较。**绝不拿解密后的压缩数据算 CRC**（zip 记录的是未压缩内容 CRC）。
 *
 * 性能优化：
 * - 预计算前 `len/2` 位口令对应的密钥状态（6 位数字即前 3 位 1000 个状态），后 `len-len/2` 位内层循环，
 *   避免重复派生前段密钥；
 * - [Dispatchers.Default] 上按 `availableProcessors()` 分片并发；
 * - 每 [PROGRESS_BATCH] 个候选检查一次协程取消并回调进度。
 *
 * 纯 Kotlin、不 import `android.*`，JVM 可单测。
 */
class ZipPasswordCracker @Inject constructor() {

    /**
     * 穷举口令。
     * @param digits 口令长度范围，默认 6..6
     * @param charset 字符集，默认 "0123456789"
     * @param onProgress 进度回调（累计已尝试候选数；**多 worker 并发回调，实现须线程安全**）
     * @return 命中的口令；未命中返回 null
     */
    suspend fun crack(
        zipFile: File,
        digits: IntRange = 6..6,
        charset: String = "0123456789",
        onProgress: ((tried: Long) -> Unit)? = null,
    ): String? {
        val bytes = try {
            zipFile.readBytes()
        } catch (e: Exception) {
            return null
        }
        val entry = ZipStructureReader.parseEntry0(bytes) ?: return null
        if (!entry.isZipCrypto) return null
        val start = entry.localDataOffset
        val end = start + entry.packedSize
        if (start < 0 || end > bytes.size || entry.packedSize < HEADER_LEN) return null

        val ctx = CipherContext(
            entry = entry,
            header = bytes.copyOfRange(start.toInt(), start.toInt() + HEADER_LEN),
            full = bytes.copyOfRange(start.toInt(), end.toInt()),
            checkCrc = ((entry.crc32 ushr 24) and 0xFF).toInt(),
            checkTime = (entry.modTime ushr 8) and 0xFF,
        )

        val cs = IntArray(charset.length) { charset[it].code }
        if (cs.isEmpty()) return null
        val base = cs.size
        val tried = AtomicLong(0)

        for (len in digits) {
            if (len <= 0) continue
            val prefixLen = len / 2
            val suffixLen = len - prefixLen
            val prefixCountL = pow(base, prefixLen)
            val suffixCountL = pow(base, suffixLen)
            if (prefixCountL <= 0 || suffixCountL <= 0) continue
            if (prefixCountL > Int.MAX_VALUE / 3 || suffixCountL > Int.MAX_VALUE) continue
            val found = searchLength(
                len = len,
                prefixLen = prefixLen,
                suffixLen = suffixLen,
                prefixCount = prefixCountL.toInt(),
                suffixCount = suffixCountL.toInt(),
                cs = cs,
                base = base,
                ctx = ctx,
                tried = tried,
                onProgress = onProgress,
            )
            if (found != null) return found
        }
        return null
    }

    /** 预计算前 [prefixLen] 位密钥状态，再按前缀分片并发穷举后缀。 */
    private suspend fun searchLength(
        len: Int,
        prefixLen: Int,
        suffixLen: Int,
        prefixCount: Int,
        suffixCount: Int,
        cs: IntArray,
        base: Int,
        ctx: CipherContext,
        tried: AtomicLong,
        onProgress: ((Long) -> Unit)?,
    ): String? = coroutineScope {
        // 预计算前 prefixLen 位对应的密钥状态（6 位数字即 1000 个三密钥状态）。
        val prefixStates = IntArray(prefixCount * 3)
        val prefixScratch = IntArray(prefixLen.coerceAtLeast(1))
        for (p in 0 until prefixCount) {
            ensureActive()
            derivePrefixState(p, prefixLen, cs, base, prefixScratch, prefixStates, p * 3)
        }

        // 并发分片：把 prefixCount 个前缀切成至多 availableProcessors() 份。
        val sliceCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1).coerceAtMost(prefixCount)
        val per = (prefixCount + sliceCount - 1) / sliceCount
        val slices = ArrayList<Pair<Int, Int>>(sliceCount)
        for (w in 0 until sliceCount) {
            val lo = w * per
            val hi = minOf(lo + per, prefixCount)
            if (lo < hi) slices.add(lo to hi)
        }

        val result = CompletableDeferred<String?>()
        val pending = AtomicInteger(slices.size)
        val jobs = slices.map { slice ->
            launch(Dispatchers.Default) {
                try {
                    val pwd = searchSlice(
                        lo = slice.first,
                        hi = slice.second,
                        len = len,
                        prefixLen = prefixLen,
                        suffixLen = suffixLen,
                        suffixCount = suffixCount,
                        cs = cs,
                        base = base,
                        prefixStates = prefixStates,
                        ctx = ctx,
                        tried = tried,
                        onProgress = onProgress,
                    )
                    if (pwd != null) result.complete(pwd)
                } finally {
                    // 全部分片结束仍未命中 → complete(null)，避免 await 永远挂起。
                    if (pending.decrementAndGet() == 0) result.complete(null)
                }
            }
        }

        val answer = result.await()
        jobs.forEach { it.cancel() }
        answer
    }

    /** 单个分片：遍历 [lo, hi) 前缀 × 全部后缀，快筛命中后做权威判定。 */
    private suspend fun searchSlice(
        lo: Int,
        hi: Int,
        len: Int,
        prefixLen: Int,
        suffixLen: Int,
        suffixCount: Int,
        cs: IntArray,
        base: Int,
        prefixStates: IntArray,
        ctx: CipherContext,
        tried: AtomicLong,
        onProgress: ((Long) -> Unit)?,
    ): String? {
        val suffixChars = IntArray(suffixLen)
        var batch = 0L
        for (p in lo until hi) {
            val k0p = prefixStates[p * 3]
            val k1p = prefixStates[p * 3 + 1]
            val k2p = prefixStates[p * 3 + 2]
            for (s in 0 until suffixCount) {
                indexToChars(s, suffixLen, cs, base, suffixChars)
                var k0 = k0p
                var k1 = k1p
                var k2 = k2p
                for (j in 0 until suffixLen) {
                    k0 = ZipCrypto.crc32(k0, suffixChars[j])
                    k1 = (k1 + (k0 and 0xFF)) * 134775813 + 1
                    k2 = ZipCrypto.crc32(k2, (k1 ushr 24) and 0xFF)
                }
                if (quickScreen(k0, k1, k2, ctx.header, ctx.checkCrc, ctx.checkTime)) {
                    val password = buildPassword(p, prefixLen, s, suffixLen, cs, base, len)
                    if (verifyPassword(password, ctx.entry, ctx.full)) return password
                }
                if (++batch >= PROGRESS_BATCH) {
                    batch = 0
                    currentCoroutineContext().ensureActive()
                    onProgress?.invoke(tried.addAndGet(PROGRESS_BATCH))
                }
            }
        }
        if (batch > 0) {
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(tried.addAndGet(batch))
        }
        return null
    }

    private companion object {
        const val PROGRESS_BATCH = 4096L
    }
}

/** 快筛所需的预读数据与校验字节。 */
private class CipherContext(
    val entry: ZipEntryInfo,
    val header: ByteArray,
    val full: ByteArray,
    val checkCrc: Int,
    val checkTime: Int,
)

/** 快筛：只解密 12 字节头，取下标 11 校验字节，等于 CRC 高字节或 DOS 时间高字节任一即通过。 */
private fun quickScreen(k0: Int, k1: Int, k2: Int, header: ByteArray, checkCrc: Int, checkTime: Int): Boolean {
    var a = k0
    var b = k1
    var c = k2
    for (i in 0 until 11) {
        val temp = (c or 2) and 0xFFFF
        val plain = (header[i].toInt() and 0xFF) xor (((temp * (temp xor 1)) ushr 8) and 0xFF)
        a = ZipCrypto.crc32(a, plain)
        b = (b + (a and 0xFF)) * 134775813 + 1
        c = ZipCrypto.crc32(c, (b ushr 24) and 0xFF)
    }
    val temp = (c or 2) and 0xFFFF
    val check = (header[11].toInt() and 0xFF) xor (((temp * (temp xor 1)) ushr 8) and 0xFF)
    return check == checkCrc || check == checkTime
}

/**
 * 权威判定：整包解密 → 解压 → 对**解压后的原始内容**算 CRC32 与中央目录 CRC 比较。
 * 错误口令的「压缩数据」是随机字节，[Inflater] 会很快抛 [DataFormatException]，故增量喂入、尽早失败。
 */
private fun verifyPassword(password: String, entry: ZipEntryInfo, full: ByteArray): Boolean {
    val state = ZipCrypto.derive(password)
    if (full.size < HEADER_LEN) return false

    if (entry.method == 0) {
        // stored：无压缩，直接对解密后的内容算 CRC。
        val crc = CRC32()
        for (i in 0 until HEADER_LEN) ZipCrypto.decryptByte(state, full[i].toInt() and 0xFF)
        for (i in HEADER_LEN until full.size) crc.update(ZipCrypto.decryptByte(state, full[i].toInt() and 0xFF))
        return crc.value == entry.crc32
    }
    if (entry.method != 8) return false

    val inflater = Inflater(true)
    return try {
        val crc = CRC32()
        var produced = 0L
        // 丢弃 12 字节加密头。
        for (i in 0 until HEADER_LEN) ZipCrypto.decryptByte(state, full[i].toInt() and 0xFF)

        val inBuf = ByteArray(512)
        val outBuf = ByteArray(8192)
        var pos = HEADER_LEN
        var finished = false
        while (pos < full.size && !finished) {
            val n = minOf(inBuf.size, full.size - pos)
            for (i in 0 until n) {
                inBuf[i] = ZipCrypto.decryptByte(state, full[pos + i].toInt() and 0xFF).toByte()
            }
            pos += n
            inflater.setInput(inBuf, 0, n)
            while (true) {
                val m = try {
                    inflater.inflate(outBuf)
                } catch (e: DataFormatException) {
                    return false // 错误口令：解密产物非合法 raw deflate
                }
                if (m > 0) {
                    crc.update(outBuf, 0, m)
                    produced += m
                    if (produced > entry.unpackedSize) return false
                } else if (inflater.finished()) {
                    finished = true
                    break
                } else if (inflater.needsInput()) {
                    break
                } else {
                    return false // 需预设字典 / 无进展
                }
            }
        }
        inflater.finished() && produced == entry.unpackedSize && crc.value == entry.crc32
    } finally {
        inflater.end()
    }
}

/** 由三密钥态派生前 [prefixLen] 位口令的密钥状态，写入 dst[dstOff..dstOff+2]。 */
private fun derivePrefixState(
    prefixIndex: Int,
    prefixLen: Int,
    cs: IntArray,
    base: Int,
    scratch: IntArray,
    dst: IntArray,
    dstOff: Int,
) {
    var a = ZipCrypto.KEY0_INIT
    var b = ZipCrypto.KEY1_INIT
    var c = ZipCrypto.KEY2_INIT
    indexToChars(prefixIndex, prefixLen, cs, base, scratch)
    for (j in 0 until prefixLen) {
        a = ZipCrypto.crc32(a, scratch[j])
        b = (b + (a and 0xFF)) * 134775813 + 1
        c = ZipCrypto.crc32(c, (b ushr 24) and 0xFF)
    }
    dst[dstOff] = a
    dst[dstOff + 1] = b
    dst[dstOff + 2] = c
}

/** 把候选序号按 base 进制展开成字符集字符码（最高位在前），写入 out[outOff..outOff+len-1]。 */
private fun indexToChars(index: Int, len: Int, cs: IntArray, base: Int, out: IntArray, outOff: Int = 0) {
    var x = index
    for (j in len - 1 downTo 0) {
        out[outOff + j] = cs[x % base]
        x /= base
    }
}

/** 拼出完整候选口令字符串（仅快筛命中时调用，频率低）。 */
private fun buildPassword(
    prefixIndex: Int,
    prefixLen: Int,
    suffixIndex: Int,
    suffixLen: Int,
    cs: IntArray,
    base: Int,
    len: Int,
): String {
    val buf = IntArray(len)
    indexToChars(prefixIndex, prefixLen, cs, base, buf, 0)
    indexToChars(suffixIndex, suffixLen, cs, base, buf, prefixLen)
    return buildString(len) { for (j in 0 until len) append(buf[j].toChar()) }
}

/** base^exp；溢出返回 -1。 */
private fun pow(base: Int, exp: Int): Long {
    var r = 1L
    repeat(exp) {
        if (r > Long.MAX_VALUE / base) return -1L
        r *= base
    }
    return r
}
