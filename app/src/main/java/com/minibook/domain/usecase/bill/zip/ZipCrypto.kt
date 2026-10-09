package com.minibook.domain.usecase.bill.zip

/**
 * ZipCrypto（PKWARE 传统加密，即「Zip 2.0 加密」）核心算法。
 *
 * 纯 Kotlin、不 import `android.*`，JVM 可单测。算法为已验证口径，**不得改动**：
 * - 三密钥初始化 `0x12345678 / 0x23456789 / 0x34567890`；
 * - [update] 用**明文字节**推进密钥（加密/解密对称，均用明文而非密文）；
 * - [keyStreamByte] 由 k2 派生密钥流字节；
 * - 解密 `plain = cipher xor keystream`，随后用 plain 更新密钥。
 *
 * Kotlin 的 `Int` 加减乘溢出即按 32 位回绕，与 PKWARE 规范的无符号 32 位算术语义一致。
 * 本对象仅供同包内 Inspector / Cracker / Extractor 与单测使用，不对外暴露。
 */
internal object ZipCrypto {

    const val KEY0_INIT = 0x12345678
    const val KEY1_INIT = 0x23456789
    const val KEY2_INIT = 0x34567890

    /** 反射多项式 CRC32 查找表（256 项）。 */
    val TABLE: IntArray = IntArray(256).also { table ->
        for (i in 0..255) {
            var c = i
            repeat(8) {
                c = if ((c and 1) != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
            }
            table[i] = c
        }
    }

    /** 三密钥可变状态（避免热循环内反复分配对象）。 */
    class KeyState(
        var k0: Int = KEY0_INIT,
        var k1: Int = KEY1_INIT,
        var k2: Int = KEY2_INIT,
    )

    /** 反射多项式 CRC32 单步推进：`(crc ushr 8) xor TABLE[(crc xor b) and 0xFF]`。 */
    fun crc32(crc: Int, b: Int): Int = (crc ushr 8) xor TABLE[(crc xor b) and 0xFF]

    /** 由 k2 派生密钥流字节。 */
    fun keyStreamByte(k2: Int): Int {
        val temp = (k2 or 2) and 0xFFFF
        return ((temp * (temp xor 1)) ushr 8) and 0xFF
    }

    /** 用明文字节 c 推进三密钥（规范公式，Int 溢出即 32 位回绕）。 */
    fun update(state: KeyState, c: Int) {
        state.k0 = crc32(state.k0, c)
        state.k1 = (state.k1 + (state.k0 and 0xFF)) * 134775813 + 1
        state.k2 = crc32(state.k2, (state.k1 ushr 24) and 0xFF)
    }

    /** 解密一个字节并推进密钥（用**明文**推进）。 */
    fun decryptByte(state: KeyState, cipher: Int): Int {
        val plain = cipher xor keyStreamByte(state.k2)
        update(state, plain)
        return plain
    }

    /** 加密一个字节并推进密钥（用明文推进，与解密对称）。 */
    fun encryptByte(state: KeyState, plain: Int): Int {
        val cipher = plain xor keyStreamByte(state.k2)
        update(state, plain)
        return cipher
    }

    /** 由口令派生三密钥态。 */
    fun derive(password: String): KeyState {
        val state = KeyState()
        for (ch in password) update(state, ch.code)
        return state
    }
}
