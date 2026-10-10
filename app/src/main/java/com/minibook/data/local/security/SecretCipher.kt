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
 * 小段敏感文本的加解密出口（JVM 单测用假实现替换）。
 *
 * 目前唯一用途：云同步的 access/refresh 令牌（DEC-034「令牌必须 Keystore 包裹后落盘」）。
 */
interface SecretCipher {
    /** 明文 → 密文（Base64 可落盘字符串）。 */
    fun encrypt(plaintext: String): String

    /** [encrypt] 的逆操作；密文损坏/钥匙失效时抛异常。 */
    fun decrypt(blob: String): String
}

/**
 * Android Keystore 包裹实现（AES-256/GCM，别名 `minibook_sync_token`）。
 *
 * 与 SQLCipher master key 的包裹 key（`KeyManager.ALIAS_*`）**刻意分开**：
 * ① DEC-002 的钥匙体系不混用；② 开库路径已过真机验证，不动它可把改动半径限制在同步功能内。
 *
 * 本 key **无用户认证要求**（`setUserAuthenticationRequired` 不设）：同步是静默行为，
 * 不应因为应用锁而无法定时刷新令牌；令牌本身也不足以解密账目（那是 master key 的职责）。
 *
 * 密文布局：Base64(`12B IV || GCM ciphertext`)。
 */
class KeystoreSecretCipher(
    private val alias: String = SYNC_TOKEN_ALIAS,
) : SecretCipher {

    companion object {
        /** 云同步令牌包裹 key 别名（与 `KeyManager` 的两个 master key 别名不重名）。 */
        const val SYNC_TOKEN_ALIAS = "minibook_sync_token"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val keyStoreLock = Any()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /** 取别名对应 key；不存在则懒创建（首次同步登录时生成）。 */
    private fun secretKey(): SecretKey = synchronized(keyStoreLock) {
        (keyStore.getKey(alias, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    override fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        require(bytes.size > GCM_IV_LENGTH) { "令牌密文格式非法" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes.copyOfRange(0, GCM_IV_LENGTH)),
        )
        return String(cipher.doFinal(bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)), Charsets.UTF_8)
    }
}
