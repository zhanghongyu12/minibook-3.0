package com.minibook.data.local.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 邮箱授权码专用 Keystore 加解密（F-13 邮件账单自动导入）。
 *
 * 与数据库 master key 体系（[KeyManager]）**严格隔离**：使用全新别名
 * [ALIAS]（`minibook_mail_credential_key`），绝不复用/触碰 `minibook_master_key_auth` /
 * `minibook_master_key_plain` 及其包裹逻辑。两者加密的对象、生命周期、失效场景完全不同
 * （邮箱凭据重置不应影响数据库开库，反之亦然）。
 *
 * 密钥语义：AES-256/GCM（`AES/GCM/NoPadding`，12B IV + 128bit tag），
 * `setUserAuthenticationRequired(false)`——邮箱凭据的读取方是后台 IMAP 拉取任务，
 * 不能要求每次使用都过生物识别。按 [KeyManager] 同口径**懒创建**：首次使用时才生成。
 *
 * 安全纪律：**授权码明文绝不出现在任何日志/异常 message 中**；本类抛出的异常仅含
 * 密文结构类描述（格式非法/解密失败），不携带明文。
 */
class MailCredentialCipher {

    companion object {
        /** 邮箱授权码专用 Keystore 别名（与 KeyManager 的 master key 别名严格分开）。 */
        const val ALIAS = "minibook_mail_credential_key"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val keyStoreLock = Any()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /** 取授权码专用 key；不存在则懒创建（无用户认证要求）。 */
    private fun getSecretKey(): SecretKey = synchronized(keyStoreLock) {
        keyStore.getKey(ALIAS, null) as? SecretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    /**
     * 加密授权码明文。
     *
     * @return Base64(12B IV || GCM ciphertext)，[Base64.NO_WRAP]（同 [KeyManager] blob 口径）。
     */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, blob, 0, iv.size)
        System.arraycopy(ciphertext, 0, blob, iv.size, ciphertext.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    /**
     * 解密 [encrypt] 产出的 blob，还原授权码明文。
     *
     * Android 平台 Cipher 无 `init(int, AlgorithmParameterSpec)` 两参重载，GCM 解密须在
     * 初始化时同时提供 key 与 IV（同 [KeyManager.createDecryptCipher] 的三参 init 口径）。
     *
     * @throws IllegalStateException 密文损坏（Base64 非法 / 结构不对 / GCM tag 校验失败）或
     *                               Keystore 密钥失效时。异常 message 不含明文。
     */
    fun decrypt(blob: String): String {
        val data = try {
            Base64.decode(blob, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("邮箱授权码密文编码非法", e)
        }
        if (data.size <= GCM_IV_LENGTH) {
            throw IllegalStateException("邮箱授权码密文结构非法")
        }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            // AEADBadTagException（密文被篡改/不匹配）/ KeyPermanentlyInvalidatedException 等
            // 统一折叠；message 只描述故障类别，绝不附带明文。
            throw IllegalStateException("邮箱授权码解密失败（密文损坏或密钥失效）", e)
        }
    }
}
