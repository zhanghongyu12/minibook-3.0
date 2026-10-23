package com.minibook.data.local.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import javax.crypto.Cipher

/**
 * BiometricPrompt 封装（ARCHITECTURE §7.4/§7.5、DEC-003、TASK-017）。
 *
 * 负责把 [KeyManager.createDecryptCipher] 产出的解包 Cipher 包进
 * `BiometricPrompt.CryptoObject`，经系统生物识别/系统凭据认证后，把已被 Keystore
 * 「激活」（authorized）的 Cipher 交回调用方，调用方再以 [KeyManager.decryptBlobWith]
 * 解出 master key——**解密动作必须绑定在 CryptoObject 上，Keystore 才会放行 auth-gated
 * key**（ARCHITECTURE §7.3/§7.4）。
 *
 * 认证配置：`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`（指纹/人脸 + 系统 PIN/图案/密码），
 * 符合 PRD F-05「系统凭据回退」；启用 DEVICE_CREDENTIAL 后不设负面按钮，由系统处理取消。
 *
 * 设计约定：
 * - **不持有 [FragmentActivity] 长引用**——每次 [authenticate] 显式传入 activity，由
 *   调用方（AppLockController / TASK-022 锁屏 UI）管理生命周期，规避单例持有 Activity 泄漏。
 * - [isDeviceSecure] 供 DEC-003 分诊：设备无系统锁屏时 auth-gated key 无法可靠认证，
 *   调用方应引导设锁屏，**绝不降级明文**（本类与 AppLockController 均不降级）。
 *
 * 线程：androidx.biometric 要求回调在主线程（ARCHITECTURE §5.2），本类回调即主线程；
 * Keystore 解密（[KeyManager.decryptBlobWith]）由调用方自行切后台，TASK-018
 * DispatcherModule 收口后统一走 `@IoDispatcher` 限定符。
 */
class BiometricHelper(
    private val context: Context,
) {

    /**
     * 设备是否已设系统锁屏（PIN/图案/密码/生物识别，[KeyguardManager.isDeviceSecure]）。
     *
     * DEC-003 硬约束：返回 false 表示 auth-gated Keystore key 无法可靠认证，锁「开启」时
     * 应引导用户去系统设置设锁屏，**不降级为明文**。
     */
    fun isDeviceSecure(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * 设备是否已录入至少一种生物识别（指纹/人脸）——2026-10-13（F-16j）。
     *
     * 为什么单独分诊：Android 13+ 的 Keystore 在生成「每次使用都要用户认证」的 key
     * （[KeyManager] 的 `setUserAuthenticationRequired(true)`，不设 validity duration）时，
     * 要求设备**至少录入一个生物识别**；只设了系统锁屏 PIN/图案/密码的设备会抛
     * `InvalidAlgorithmParameterException: At least one biometric must be enrolled to create
     * keys requiring user authentication for every use`。该异常发生在主线程且未被捕获 ⇒
     * 应用直接崩溃（模拟器实测：设 PIN 后开启应用锁即崩，PID 4040，FATAL EXCEPTION: main）。
     *
     * 口径：`BIOMETRIC_STRONG` 或 `BIOMETRIC_WEAK` 任一 `BIOMETRIC_SUCCESS` 即视为已录入
     * （**刻意不并入 DEVICE_CREDENTIAL**——只有 PIN 的情况下它也会返回 SUCCESS，正是崩溃场景）。
     */
    fun hasEnrolledBiometric(): Boolean =
        canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ||
            canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)

    private fun canAuthenticate(authenticators: Int): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * 启动一次 BiometricPrompt 认证。
     *
     * @param activity 当前 [FragmentActivity]（仅本次认证期间引用，不长期持有）。
     * @param cipher [KeyManager.createDecryptCipher] 产出的解包 Cipher（未认证，须经本方法
     *               激活；锁「开启」路径传 `createDecryptCipher(useAuth = true)`）。
     * @param onSuccess 认证成功回调，参数为已授权 Cipher（`AuthenticationResult.cryptoObject`
     *                  的 cipher）；调用方应立即 [KeyManager.decryptBlobWith] 解出 master key
     *                  并用后清零（ARCHITECTURE §7.2）。
     * @param onError 认证失败/取消回调（`errorCode` + 系统提示文案）。
     */
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(TITLE)
            .setSubtitle(SUBTITLE)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authorizedCipher = result.cryptoObject?.cipher
                if (authorizedCipher != null) {
                    onSuccess(authorizedCipher)
                } else {
                    onError(ERROR_CIPHER_NULL, "认证成功但 cryptoObject.cipher 为空")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString)
            }

            // onAuthenticationFailed：非终态（提示音后用户可重试），按 BiometricPrompt
            // 语义不回调错误；多次失败/取消由 onAuthenticationError 终态收敛。
        }

        BiometricPrompt(activity, executor, callback)
            .authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    companion object {
        private const val TITLE = "解锁 MiniBook"
        private const val SUBTITLE = "使用指纹/人脸或系统凭据验证身份"

        /** 认证成功但取不到 cipher 的哨兵错误码（正常认证流程不会出现）。 */
        private const val ERROR_CIPHER_NULL = Int.MIN_VALUE
    }
}
