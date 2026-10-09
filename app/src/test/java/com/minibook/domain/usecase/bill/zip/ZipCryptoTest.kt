package com.minibook.domain.usecase.bill.zip

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ZipCrypto 算法与 EncryptedZipExtractor 单测（含已知答案自校验）。
 *
 * 自校验口径：测试内用**独立参考实现**（[ReferenceZipCrypto]，不复用被测 [ZipCrypto]）造出
 * 已知口令（`123456`）的加密 zip，再断言：
 * - (a) 正确口令能解密解压出原始内容（method=8 deflate 与 method=0 stored 两种）；
 * - (b) 错误口令抛异常；
 * - (c) 硬编码的「口令→三密钥 / 已知密文头→明文」已知答案向量与规范一致。
 *
 * 另覆盖：GBK 中文文件名（无 UTF-8 标志）正确解出、校验字节为 DOS 时间高字节（宽松接受）、
 * [ZipArchiveInspector] 加密类型判定。全部自造内存 zip，不依赖任何外部文件。
 */
class ZipCryptoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---------- 已知答案自校验（强制） ----------

    @Test
    fun `已知答案 - 口令 123456 派生三密钥`() {
        val s = ZipCrypto.derive("123456")
        assertEquals(0x8879dfed.toInt(), s.k0)
        assertEquals(0x14335b6b, s.k1)
        assertEquals(0x8dc58b53.toInt(), s.k2)
    }

    @Test
    fun `已知答案 - 解密已知密文头得到校验字节 0x15`() {
        val cipher = hex("504539b17d2fe4a633136a68")
        val state = ZipCrypto.derive("123456")
        val plain = ByteArray(12) { ZipCrypto.decryptByte(state, cipher[it].toInt() and 0xFF).toByte() }
        assertArrayEquals(hex("e13b032e112a32b579080f15"), plain)
        assertEquals(0x15, plain[11].toInt() and 0xFF)
    }

    // ---------- 正确/错误口令 + 两种压缩方法 ----------

    @Test
    fun `deflate - 正确口令解出原始内容`() {
        val content = "微信支付账单\n2026-08-01,商户消费,100.00\n".toByteArray(Charsets.UTF_8)
        val zipFile = tmp.newFile("bill-deflate.zip")
        zipFile.writeBytes(buildZip("微信支付账单.csv", content, method = 8, password = "123456"))
        val out = EncryptedZipExtractor().extract(zipFile, "123456", tmp.newFolder("out-deflate"))
        assertArrayEquals(content, out.readBytes())
        assertEquals("微信支付账单.csv", out.name)
    }

    @Test
    fun `deflate - 错误口令抛异常`() {
        val zipFile = tmp.newFile("bill-bad.zip")
        zipFile.writeBytes(buildZip("bill.csv", "data".toByteArray(), method = 8, password = "123456"))
        try {
            EncryptedZipExtractor().extract(zipFile, "000000", tmp.newFolder("out-bad"))
            throw AssertionError("错误口令应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 预期
        }
    }

    @Test
    fun `stored - 正确口令解出原始内容`() {
        val content = "支付宝账单内容\n2026-08-02,转账,88.88\n".toByteArray(Charsets.UTF_8)
        val zipFile = tmp.newFile("bill-stored.zip")
        zipFile.writeBytes(buildZip("支付宝账单.csv", content, method = 0, password = "123456"))
        val out = EncryptedZipExtractor().extract(zipFile, "123456", tmp.newFolder("out-stored"))
        assertArrayEquals(content, out.readBytes())
    }

    // ---------- 校验字节宽松接受（data descriptor 场景） ----------

    @Test
    fun `校验字节为 DOS 时间高字节 - 仍能正确解出`() {
        val content = "content-with-time-check".toByteArray()
        val zipFile = tmp.newFile("bill-time.zip")
        zipFile.writeBytes(buildZip("bill.csv", content, method = 8, password = "123456", checkByteFromTime = true))
        val out = EncryptedZipExtractor().extract(zipFile, "123456", tmp.newFolder("out-time"))
        assertArrayEquals(content, out.readBytes())
    }

    // ---------- 条目名编码 ----------

    @Test
    fun `GBK 文件名 - 无 UTF8 标志也能解出中文名`() {
        val zipFile = tmp.newFile("gbk-name.zip")
        zipFile.writeBytes(buildZip("微信支付账单.csv", "x".toByteArray(), method = 8, password = "123456", utf8Name = false))
        val out = EncryptedZipExtractor().extract(zipFile, "123456", tmp.newFolder("out-gbk"))
        assertEquals("微信支付账单.csv", out.name)
    }

    // ---------- 加密类型探测 ----------

    @Test
    fun `inspect - ZipCrypto`() {
        val zipFile = tmp.newFile("inspect-zc.zip")
        zipFile.writeBytes(buildZip("bill.csv", "x".toByteArray(), method = 8, password = "123456"))
        assertEquals(ZipEncryptionInfo.ZipCrypto, ZipArchiveInspector().inspect(zipFile))
    }

    @Test
    fun `inspect - 未加密返回 Unsupported`() {
        val zipFile = tmp.newFile("inspect-plain.zip")
        zipFile.writeBytes(buildZip("bill.csv", "x".toByteArray(), method = 8, password = null))
        assertTrue(ZipArchiveInspector().inspect(zipFile) is ZipEncryptionInfo.Unsupported)
    }

    @Test
    fun `inspect - method 99 返回 Unsupported`() {
        val zipFile = tmp.newFile("inspect-aes.zip")
        zipFile.writeBytes(buildZip("bill.csv", "x".toByteArray(), method = 99, password = null))
        assertTrue(ZipArchiveInspector().inspect(zipFile) is ZipEncryptionInfo.Unsupported)
    }

    @Test
    fun `inspect - extra field 0x9901 返回 Unsupported`() {
        val zipFile = tmp.newFile("inspect-aes-extra.zip")
        zipFile.writeBytes(buildZip("bill.csv", "x".toByteArray(), method = 8, password = null, aesExtra = true))
        assertTrue(ZipArchiveInspector().inspect(zipFile) is ZipEncryptionInfo.Unsupported)
    }

    @Test
    fun `inspect - 文件不存在返回 Unsupported`() {
        assertTrue(ZipArchiveInspector().inspect(File(tmp.root, "no-such.zip")) is ZipEncryptionInfo.Unsupported)
    }
}

// =====================================================================================
// 测试夹具（internal，供 ZipPasswordCrackerTest 复用；同测试模块内可见）
// =====================================================================================

/**
 * 独立参考实现：与规范一致的 ZipCrypto **加密**（不复用被测 [ZipCrypto]），用于造已知口令的加密 zip。
 */
internal object ReferenceZipCrypto {
    private val table = IntArray(256).also { t ->
        for (i in 0..255) {
            var c = i
            repeat(8) { c = if ((c and 1) != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
            t[i] = c
        }
    }

    private fun crc32(crc: Int, b: Int): Int = (crc ushr 8) xor table[(crc xor b) and 0xFF]

    /** 加密：密钥流 = cipher = plain xor keyStreamByte(k2)，随后用明文更新密钥。 */
    fun encrypt(plain: ByteArray, password: String): ByteArray {
        var k0 = 0x12345678
        var k1 = 0x23456789
        var k2 = 0x34567890
        fun update(c: Int) {
            k0 = crc32(k0, c)
            k1 = (k1 + (k0 and 0xFF)) * 134775813 + 1
            k2 = crc32(k2, (k1 ushr 24) and 0xFF)
        }
        for (ch in password) update(ch.code)
        val out = ByteArray(plain.size)
        for (i in plain.indices) {
            val temp = (k2 or 2) and 0xFFFF
            val k = ((temp * (temp xor 1)) ushr 8) and 0xFF
            out[i] = ((plain[i].toInt() and 0xFF) xor k).toByte()
            update(plain[i].toInt() and 0xFF)
        }
        return out
    }
}

/**
 * 构造单条目 zip（全部内存拼装，little-endian）。
 * @param password null=未加密；非 null=ZipCrypto 加密（12 字节头 + 压缩数据）。
 * @param checkByteFromTime true=校验字节取 DOS 时间高字节（模拟 data descriptor 变体）。
 * @param aesExtra true=中央目录与本地头附加 header id 0x9901 的 extra field（模拟 WinZip AES 探测）。
 */
internal fun buildZip(
    name: String,
    content: ByteArray,
    method: Int,
    password: String?,
    utf8Name: Boolean = true,
    checkByteFromTime: Boolean = false,
    setDataDescriptor: Boolean = false,
    aesExtra: Boolean = false,
): ByteArray {
    val nameBytes = if (utf8Name) name.toByteArray(Charsets.UTF_8) else name.toByteArray(Charset.forName("GBK"))
    val crc = CRC32().apply { update(content) }.value
    val modTime = 0x4A21
    val modDate = 0x5721
    val extraField = if (aesExtra) buildAesExtra() else ByteArray(0)

    val compressed = when (method) {
        0 -> content
        8 -> rawDeflate(content)
        else -> content // 99 等仅占位，供 inspect 判型
    }
    var flag = 0
    if (password != null) flag = flag or 0x0001
    if (utf8Name) flag = flag or 0x0800
    if (setDataDescriptor) flag = flag or 0x0008

    val data: ByteArray = if (password != null) {
        val check = if (checkByteFromTime) ((modTime ushr 8) and 0xFF).toByte() else ((crc ushr 24) and 0xFF).toByte()
        val header = ByteArray(12)
        val rnd = Random(1234)
        for (i in 0 until 11) header[i] = rnd.nextInt(256).toByte()
        header[11] = check
        ReferenceZipCrypto.encrypt(header + compressed, password)
    } else {
        compressed
    }

    val local = ByteArrayOutputStream()
    putU32(local, 0x04034b50L)
    putU16(local, 20) // version needed
    putU16(local, flag)
    putU16(local, method)
    putU16(local, modTime)
    putU16(local, modDate)
    putU32(local, crc)
    putU32(local, data.size.toLong()) // packed size（加密时含 12 字节头）
    putU32(local, content.size.toLong())
    putU16(local, nameBytes.size)
    putU16(local, extraField.size)
    local.write(nameBytes)
    local.write(extraField)
    local.write(data)

    val central = ByteArrayOutputStream()
    putU32(central, 0x02014b50L)
    putU16(central, 20) // version made by
    putU16(central, 20) // version needed
    putU16(central, flag)
    putU16(central, method)
    putU16(central, modTime)
    putU16(central, modDate)
    putU32(central, crc)
    putU32(central, data.size.toLong())
    putU32(central, content.size.toLong())
    putU16(central, nameBytes.size)
    putU16(central, extraField.size)
    putU16(central, 0) // comment len
    putU16(central, 0) // disk start
    putU16(central, 0) // internal attrs
    putU32(central, 0L) // external attrs
    putU32(central, 0L) // local header offset（单条目恒为 0）
    central.write(nameBytes)
    central.write(extraField)

    val localBytes = local.toByteArray()
    val centralBytes = central.toByteArray()

    val eocd = ByteArrayOutputStream()
    putU32(eocd, 0x06054b50L)
    putU16(eocd, 0)
    putU16(eocd, 0)
    putU16(eocd, 1)
    putU16(eocd, 1)
    putU32(eocd, centralBytes.size.toLong())
    putU32(eocd, localBytes.size.toLong())
    putU16(eocd, 0)

    return ByteArrayOutputStream().apply {
        write(localBytes)
        write(centralBytes)
        write(eocd.toByteArray())
    }.toByteArray()
}

/** WinZip AES extra field：id=0x9901，size=7，data 占位。 */
private fun buildAesExtra(): ByteArray = ByteArrayOutputStream().apply {
    putU16(this, 0x9901)
    putU16(this, 7)
    write(byteArrayOf(0x01, 0x00, 0x41, 0x45, 0x02, 0x00, 0x00))
}.toByteArray()

/** raw deflate（nowrap），供测试构造 method=8 的压缩数据。 */
internal fun rawDeflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    return try {
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        out.toByteArray()
    } finally {
        deflater.end()
    }
}

private fun putU16(out: ByteArrayOutputStream, v: Int) {
    out.write(v and 0xFF)
    out.write((v ushr 8) and 0xFF)
}

private fun putU32(out: ByteArrayOutputStream, v: Long) {
    out.write((v and 0xFF).toInt())
    out.write(((v ushr 8) and 0xFF).toInt())
    out.write(((v ushr 16) and 0xFF).toInt())
    out.write(((v ushr 24) and 0xFF).toInt())
}

internal fun hex(s: String): ByteArray {
    require(s.length % 2 == 0) { "hex 长度必须为偶数" }
    return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
