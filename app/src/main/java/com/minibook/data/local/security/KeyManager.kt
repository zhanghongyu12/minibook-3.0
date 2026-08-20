package com.minibook.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore 密钥管理与 SQLCipher master key 包裹/解包（ARCHITECTURE §7.2/§7.3、DEC-002）。
 *
 * 密钥体系：**一把 master key + 两把包裹 key**（DEC-002，锁可关但 DB 恒加密）。
 * - `minibook_master_key_auth`：AES-256/GCM，`setUserAuthenticationRequired(true)` +
 *   `setInvalidatedByBiometricEnrollment(true)`；应用锁「开启」时用于包裹/解包 master key。
 *   解包动作必须绑定在 `BiometricPrompt.CryptoObject` 上（见 [createDecryptCipher] 与
 *   ARCHITECTURE §7.4），生物识别/系统凭据通过后 Keystore 才放行。
 * - `minibook_master_key_plain`：AES-256/GCM，无认证要求；应用锁「关闭」时静默解包开库。
 * - 两把包裹 key 加密的是**同一** 32 字节 `SecureRandom` master key；锁状态切换时用当前
 *   包裹 key 重加密并覆盖 `noBackupFilesDir/minibook_key.bin`（DEC-002：关锁不降级为明文）。
 *
 * master key 从不出域：以密文 blob（Base64 编码 `12B IV || GCM ciphertext`）持久化于应用
 * 私有目录 `noBackupFilesDir`（不参与系统备份，DEC-006）。
 *
 * 说明：
 * - 双别名**按需懒创建**：首次使用某个 alias 时才生成对应 Keystore key。设备无系统锁屏时
 *   生成 auth-gated key 可能失败，按 DEC-003 应引导设锁屏，不降级明文——本类不自行降级。
 * - API 26/27 的 auth-gated key 认证语义与 28+ 不同（ARCHITECTURE §7.5）：低版本上
 *   `cipher.init` 即可能抛 [UserNotAuthenticatedException]，28+ 多在 `doFinal` 抛。
 *   本类两种路径均如实暴露，由调用方（TASK-017 BiometricHelper）统一编排 BiometricPrompt。
 * - Android 平台 `Cipher` 无 `init(int, AlgorithmParameterSpec)`（与 Java SE 不同），
 *   GCM 解密须在**初始化时同时提供 key 与 IV**（3 参 `init`）。因此本类在
 *   [createDecryptCipher] 内先读 blob 取 IV，再 key+IV 一并初始化。
 *
 * @property context 仅用于定位 `noBackupFilesDir`。
 */
class KeyManager(private val context: Context) {

    companion object {
        /** 应用锁开启时的包裹 key 别名（需生物识别/系统凭据认证）。 */
        const val ALIAS_AUTH = "minibook_master_key_auth"

        /** 应用锁关闭时的包裹 key 别名（无认证要求，静默开库用）。 */
        const val ALIAS_PLAIN = "minibook_master_key_plain"

        /** master key blob 文件名（`noBackupFilesDir/minibook_key.bin`，DEC-002/DEC-006）。 */
        const val KEY_BLOB_FILE_NAME = "minibook_key.bin"

        /** SQLCipher master key 长度：32 字节（ARCHITECTURE §7.1）。 */
        const val MASTER_KEY_BYTES = 32

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val keyStoreLock = Any()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /** blob 内部结构：`12B IV || GCM ciphertext`。 */
    private class BlobParts(val iv: ByteArray, val ciphertext: ByteArray)

    // ==== Keystore 钥匙管理 ====

    /**
     * 取指定别名对应的 Keystore key；不存在则按别名语义懒创建。
     * `_auth` 别名要求用户认证，`_plain` 别名无认证要求（DEC-002）。
     */
    private fun getSecretKey(alias: String): SecretKey = synchronized(keyStoreLock) {
        keyStore.getKey(alias, null) as? SecretKey
            ?: createKey(alias, requireAuthentication = alias == ALIAS_AUTH)
    }

    private fun createKey(alias: String, requireAuthentication: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (requireAuthentication) {
            builder
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            // 不设置 setUserAuthenticationValidityDurationSeconds → 每次使用都要求重新认证
            // （ARCHITECTURE §7.5；API 26/27 认证语义差异见类注释）。
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    // ==== master key 生成 ====

    /** 生成 32 字节随机 master key（SQLCipher passphrase 唯一来源，ARCHITECTURE §7.1）。 */
    fun generateMasterKey(): ByteArray {
        val key = ByteArray(MASTER_KEY_BYTES)
        SecureRandom().nextBytes(key)
        return key
    }

    // ==== blob 持久化 ====

    private fun keyBlobFile(): File = File(context.noBackupFilesDir, KEY_BLOB_FILE_NAME)

    /** 是否存在 master key blob（首次运行检测；TASK-011 Bootstrap 用于判断是否需初始化）。 */
    fun hasMasterKeyBlob(): Boolean = keyBlobFile().exists()

    private fun readBlobParts(): BlobParts {
        val file = keyBlobFile()
        check(file.exists()) { "master key blob 不存在：${file.absolutePath}" }
        val blob = Base64.decode(file.readText(), Base64.NO_WRAP)
        require(blob.size > GCM_IV_LENGTH) { "master key blob 格式非法" }
        return BlobParts(
            iv = blob.copyOfRange(0, GCM_IV_LENGTH),
            ciphertext = blob.copyOfRange(GCM_IV_LENGTH, blob.size),
        )
    }

    /**
     * 用指定包裹 key 加密 [masterKey] 并覆盖写 `minibook_key.bin`。
     *
     * 首次启动生成 master key 后调用（锁当前状态决定 `useAuth`）；
     * **锁状态切换**（开↔关，DEC-002）时用当前包裹 key 重加密覆盖，旧 blob 即失效。
     *
     * @param useAuth true=用 `_auth` 包裹 key 加密（需已认证；TASK-017 锁开启流程驱动
     *                BiometricPrompt 后调用）；false=用 `_plain` 包裹 key 加密（静默）。
     */
    fun persistMasterKey(masterKey: ByteArray, useAuth: Boolean) {
        persistMasterKeyWithCipher(masterKey, createMasterKeyEncryptCipher(useAuth))
    }

    /**
     * 创建包裹用 ENCRYPT cipher（**无 blob 依赖**，可在首次运行无 `minibook_key.bin` 时调用）。
     *
     * 首启 + 锁开启路径：`createDecryptCipher` 依赖 blob（`readBlobParts` 会抛「blob 不存在」），
     * 无法在首启时初始化；故改用本方法产出 ENCRYPT cipher 并绑定 BiometricPrompt 认证
     * （CryptoObject 使用 auth key），认证成功后以 [persistMasterKeyWithCipher] 落盘 auth 包裹
     * 的 master key（DEC-002：**禁止**锁开启时首启生成 plain 包裹）。锁关闭首启无需认证，
     * 直接 [persistMasterKey]（内部复用本方法）。
     *
     * @param useAuth true=用 `_auth` 包裹 key（需认证）；false=用 `_plain` 包裹 key（静默）。
     */
    fun createMasterKeyEncryptCipher(useAuth: Boolean): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(if (useAuth) ALIAS_AUTH else ALIAS_PLAIN))
        return cipher
    }

    /**
     * 用**已授权**的 ENCRYPT cipher 加密 [masterKey] 并覆盖写 `minibook_key.bin`。
     *
     * 与 [persistMasterKey] 的区别：cipher 由调用方经 [createMasterKeyEncryptCipher] 创建并绑定
     * BiometricPrompt 认证后传入（首启 + 锁开启路径），避免认证后新建 auth-key cipher 触发
     * [UserNotAuthenticatedException]（ARCHITECTURE §7.5 认证语义；「解密须绑定 CryptoObject」
     * 的加密侧对称用法）。
     */
    fun persistMasterKeyWithCipher(masterKey: ByteArray, encryptCipher: Cipher) {
        require(masterKey.size == MASTER_KEY_BYTES) { "master key 必须为 $MASTER_KEY_BYTES 字节" }
        val iv = encryptCipher.iv
        val ciphertext = encryptCipher.doFinal(masterKey)
        val blob = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, blob, 0, iv.size)
        System.arraycopy(ciphertext, 0, blob, iv.size, ciphertext.size)
        keyBlobFile().writeText(Base64.encodeToString(blob, Base64.NO_WRAP))
    }

    // ==== master key 解包 ====

    /**
     * 解包当前 blob 并返回 master key（**调用方使用后必须清零**，见
     * [PasswordCipher.wipe] / `Arrays.fill`，ARCHITECTURE §7.2 代码审查点）。
     *
     * @param useAuth true=用 `_auth` 包裹 key 解（要求已完成生物识别认证；否则可能抛
     *                [UserNotAuthenticatedException]，锁开启路径应改用
     *                [createDecryptCipher] + BiometricPrompt，见 ARCHITECTURE §7.4）；
     *                false=用 `_plain` 包裹 key 解（静默开库）。
     */
    fun getMasterKey(useAuth: Boolean): ByteArray {
        val cipher = createDecryptCipher(useAuth)
        return decryptBlobWith(cipher)
    }

    /**
     * 创建解包用的 Cipher：**已读 blob 取 IV，并以 key + IV 完成 GCM DECRYPT 初始化**。
     *
     * 锁开启路径（TASK-017）：将返回的 Cipher 包进 `BiometricPrompt.CryptoObject`
     * → 认证成功后用回调返回的 cipher 调 [decryptBlobWith]。锁关闭路径：用 `_plain` key
     * 初始化，无认证要求，可立即 [decryptBlobWith]（ARCHITECTURE §7.3）。
     */
    fun createDecryptCipher(useAuth: Boolean): Cipher {
        val parts = readBlobParts()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getSecretKey(if (useAuth) ALIAS_AUTH else ALIAS_PLAIN),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, parts.iv),
        )
        return cipher
    }

    /**
     * 用（已授权的）Cipher 解出 master key（调用方使用后必须清零）。
     * [cipher] 必须由 [createDecryptCipher] 创建；锁开启路径须先经 BiometricPrompt 认证。
     */
    fun decryptBlobWith(cipher: Cipher): ByteArray {
        val parts = readBlobParts()
        return cipher.doFinal(parts.ciphertext)
    }
}
