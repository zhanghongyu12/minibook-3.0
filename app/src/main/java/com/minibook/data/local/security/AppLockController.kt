package com.minibook.data.local.security

import androidx.fragment.app.FragmentActivity
import com.minibook.data.local.datastore.LockTimeout
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.di.ApplicationScope
import java.util.Arrays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用锁控制器（ARCHITECTURE §7.5/§9、TASK-017）。
 *
 * 单例（@Singleton，见 SecurityModule），持 [DatabaseGate] + [KeyManager] +
 * [SettingsDataStore] + [BiometricHelper]，驱动锁状态机：
 *
 * ```
 * Locked（DB closed · key 未解）──unlock(useAuth=true)──► Unlocked（DB open）
 *    ▲                                                       │
 *    └────── onAppBackground 闲置超阈 / lock() ◄──────────────┘
 * ```
 *
 * - [lock]：关闭 DB（[DatabaseGate.close]）+ 广播锁状态（[isLocked]）。master key 不长期
 *   驻留内存：解锁时经 [DatabaseGate.open] 打开后即由其 finally 清零（ARCHITECTURE §7.2），
 *   重锁只需关连接即无 key 引用。
 * - [unlock]：`useAuth=false` → plain key 静默开库直达（§7.4 分诊 A，锁关闭路径）；
 *   `useAuth=true` → [BiometricHelper] 认证解出 master key → [DatabaseGate.open]。
 * - 闲置超时重锁：读 [SettingsDataStore.lockTimeout]（立即/30s/1min/5min/永不），在
 *   [ApplicationScope] 协程 delay 后 [lock]；由 TASK-018 MiniBookApp 生命周期观察者接入
 *   [onAppBackground]/[onAppForeground]。
 * - 锁状态切换（开→关）：[downgradeToPlainKey] 用 plain key 重加密覆盖 blob（DEC-002，
 *   关锁不降级为明文）。
 *
 * 锁状态模型：[LockState]（ui/lock 层）推迟到 TASK-022，本期以 `StateFlow<Boolean>`
 * `isLocked` 对外广播（true=锁定，false=解锁）。
 *
 * @param applicationScope 应用级协程作用域（闲置重锁计时宿主，TASK-018 前暂由
 *                         SecurityModule 提供）。
 */
class AppLockController(
    private val databaseGate: DatabaseGate,
    private val keyManager: KeyManager,
    private val settingsDataStore: SettingsDataStore,
    private val biometricHelper: BiometricHelper,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val _isLocked = MutableStateFlow(true)
    private var idleJob: Job? = null

    /** 锁状态：true=已锁定（DB 关闭，无可达数据）；false=已解锁（DB 打开）。 */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /**
     * 锁定：关闭数据库 + 广播锁状态（ARCHITECTURE §7.5 重锁时序）。
     * 幂等：已在锁定态时直接返回（不重复关库）。
     */
    fun lock() {
        if (_isLocked.value) return
        idleJob?.cancel()
        idleJob = null
        databaseGate.close()
        _isLocked.value = true
    }

    /**
     * 解锁。
     *
     * @param useAuth true=锁「开启」路径：经 [BiometricHelper] 认证解 master key 再开库；
     *                false=锁「关闭」路径：plain key 静默开库（§7.4 分诊 A，无锁屏）。
     * @param activity 当前 [FragmentActivity]；仅 `useAuth=true` 需要（BiometricPrompt），
     *                 传 null 时按解锁失败处理。
     * @param onResult 解锁结果回调（true=成功且 [isLocked]=false；false=失败，含 DEC-003
     *                 设备无锁屏、认证取消/失败、DB 打开失败等）。
     */
    fun unlock(
        useAuth: Boolean,
        activity: FragmentActivity? = null,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        if (!_isLocked.value) {
            onResult?.invoke(true)
            return
        }
        if (!useAuth) {
            val success = databaseGate.openWithKeyManager(false)
            if (success) _isLocked.value = false
            onResult?.invoke(success)
            return
        }

        // 锁「开启」路径：先满足 DEC-003（设备已设系统锁屏），否则不降级明文。
        if (!biometricHelper.isDeviceSecure()) {
            onResult?.invoke(false)
            return
        }
        val host = activity ?: run {
            onResult?.invoke(false)
            return
        }
        val cipher = keyManager.createDecryptCipher(useAuth = true)
        biometricHelper.authenticate(
            activity = host,
            cipher = cipher,
            onSuccess = { authorizedCipher ->
                // BiometricPrompt 回调在主线程；解密 + 开库切后台（TASK-018 后走 @IoDispatcher）。
                applicationScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        // decryptBlobWith 产出 32B master key；DatabaseGate.open 内部 finally 清零。
                        databaseGate.open(keyManager.decryptBlobWith(authorizedCipher))
                    }
                    if (success) _isLocked.value = false
                    onResult?.invoke(success)
                }
            },
            onError = { _, _ -> onResult?.invoke(false) },
        )
    }

    /**
     * 锁状态切换（开→关，DEC-002）：把 master key blob 从 auth 包裹重加密为 plain 包裹。
     *
     * 用户在设置页关闭应用锁时调用（TASK-027 设置页消费）：先经 [BiometricHelper] 认证解出
     * master key，再 [KeyManager.persistMasterKey]（`useAuth=false`）覆盖 blob，master key
     * 用后立即清零（ARCHITECTURE §7.2 审查点）；成功后旧 blob 失效，此后冷启动走 plain key
     * 静默开库直达（§7.4 分诊 A）。
     *
     * @param activity 当前 [FragmentActivity]（BiometricPrompt）。
     * @param onResult 切换结果回调（true=已重加密为 plain 包裹；false=认证取消/失败或
     *                 DEC-003 设备无锁屏）。
     */
    fun downgradeToPlainKey(
        activity: FragmentActivity,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        if (!biometricHelper.isDeviceSecure()) {
            onResult?.invoke(false)
            return
        }
        val cipher = keyManager.createDecryptCipher(useAuth = true)
        biometricHelper.authenticate(
            activity = activity,
            cipher = cipher,
            onSuccess = { authorizedCipher ->
                applicationScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            val masterKey = keyManager.decryptBlobWith(authorizedCipher)
                            try {
                                keyManager.persistMasterKey(masterKey, useAuth = false)
                                true
                            } finally {
                                Arrays.fill(masterKey, 0) // ARCHITECTURE §7.2 密钥清零审查点
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    onResult?.invoke(success)
                }
            },
            onError = { _, _ -> onResult?.invoke(false) },
        )
    }

    /**
     * 应用进入后台（ON_STOP）时调用，启动闲置重锁计时（ARCHITECTURE §7.5）。
     *
     * 读 [SettingsDataStore.lockTimeout] 当前档位并映射毫秒（见 [LockTimeout.toLockDelayMillis]）：
     * 立即→0（直接重锁）、30s/1min/5min→对应毫秒、永不→-1（不重锁）。在 [ApplicationScope]
     * 协程 delay 后 [lock]；回前台未超阈由 [onAppForeground] 取消计时（§9 状态机「回前台且
     * 未超阈→恢复使用」）。已在锁定态时直接返回（无计时必要）。
     *
     * 注：本方法由 TASK-018 MiniBookApp 锁生命周期观察者接入（本期不碰 MiniBookApp.kt）。
     */
    fun onAppBackground() {
        idleJob?.cancel()
        if (_isLocked.value) return
        idleJob = applicationScope.launch {
            val timeout = settingsDataStore.lockTimeout.first()
            val millis = timeout.toLockDelayMillis()
            when {
                millis < 0 -> return@launch // NEVER：永不自动重锁
                millis == 0L -> lock() // IMMEDIATELY：立即重锁
                else -> {
                    delay(millis)
                    lock()
                }
            }
        }
    }

    /** 应用回到前台（ON_START/ON_RESUME）时调用，取消闲置重锁计时（§9 状态机「恢复」）。 */
    fun onAppForeground() {
        idleJob?.cancel()
        idleJob = null
    }
}

/**
 * [LockTimeout] → 闲置重锁毫秒（TASK-017 消费方实现，见 data/local/datastore/Enums.kt
 * 「档位到具体毫秒阈值的映射由 TASK-017 消费方实现」）。
 *
 * 负值表示「永不」；0 表示「立即」。
 */
private fun LockTimeout.toLockDelayMillis(): Long = when (this) {
    LockTimeout.IMMEDIATELY -> 0L
    LockTimeout.THIRTY_SECONDS -> 30_000L
    LockTimeout.ONE_MINUTE -> 60_000L
    LockTimeout.FIVE_MINUTES -> 300_000L
    LockTimeout.NEVER -> -1L
}
