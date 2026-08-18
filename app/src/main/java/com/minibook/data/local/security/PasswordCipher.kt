package com.minibook.data.local.security

import java.util.Arrays
import javax.inject.Inject

/**
 * SQLCipher passphrase 门面（ARCHITECTURE §7.1/§7.2）。
 *
 * SQLCipher 的 passphrase 就是 [KeyManager] 解出的 32 字节 master key 本身，无需再派生
 * （避免引入 KDF 偏差与额外随机源，DEC-002「一把 master key」）。本类仅做
 * 「取 key → 交予 DatabaseGate → 清零」的薄封装，保证密钥在内存中的生命周期最短。
 *
 * 典型用法：
 * ```
 * val passphrase = passwordCipher.obtainPassphrase(useAuth = false)
 * try {
 *     databaseGate.open(passphrase)
 * } finally {
 *     passwordCipher.wipe(passphrase)   // ARCHITECTURE §7.2 代码审查点
 * }
 * ```
 * 也可直接使用 [withPassphrase] 让本类在闭包结束后自动清零。
 */
class PasswordCipher @Inject constructor(
    private val keyManager: KeyManager,
) {

    /**
     * 取得 SQLCipher passphrase（= master key bytes，32 字节）。
     *
     * @param useAuth true=走 `_auth` 包裹 key 解包（锁开启；需已完成生物识别认证，
     *                见 [KeyManager.createDecryptCipher]）；false=`_plain` key 静默解包。
     * @return passphrase 字节数组，调用方用后必须 [wipe]（或交给会清零的接收方，
     *         如 [DatabaseGate.open]）。
     */
    fun obtainPassphrase(useAuth: Boolean): ByteArray = keyManager.getMasterKey(useAuth)

    /** 将 passphrase 字节数组清零（ARCHITECTURE §7.2 / 代码审查点）。 */
    fun wipe(passphrase: ByteArray) {
        Arrays.fill(passphrase, 0)
    }

    /**
     * 便捷：取得 passphrase 并执行 [block]，无论成功失败都在 finally 中清零。
     *
     * @param useAuth 见 [obtainPassphrase]
     */
    inline fun <T> withPassphrase(useAuth: Boolean, block: (ByteArray) -> T): T {
        val passphrase = obtainPassphrase(useAuth)
        return try {
            block(passphrase)
        } finally {
            wipe(passphrase)
        }
    }
}
