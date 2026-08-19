package com.minibook.di

import android.content.Context
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.data.local.security.AppLockController
import com.minibook.data.local.security.BiometricHelper
import com.minibook.data.local.security.DatabaseGate
import com.minibook.data.local.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * 安全层装配（ARCHITECTURE §5.1、TASK-004 + TASK-017）。
 *
 * 提供：
 * - [KeyManager]：Keystore 双别名 + master key 包裹/解包（TASK-004）。
 * - [BiometricHelper]：BiometricPrompt 封装（§7.4），@Singleton。
 * - [AppLockController]：锁状态机 + 闲置超时重锁（§7.5/§9），@Singleton；其
 *   `@ApplicationScope` 依赖由 [DispatcherModule] 提供（TASK-018 收口迁移，ARCHITECTURE
 *   §5.1 DispatcherModule 行）。
 *
 * 推迟项（编排者裁定，本期不实现）：
 * - ui/lock/LockState 锁状态模型 → TASK-022（本期 [AppLockController.isLocked] 以
 *   `StateFlow<Boolean>` 广播）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /** Keystore 密钥管理单例（懒创建双别名，见 [KeyManager] 类注释）。 */
    @Provides
    @Singleton
    fun provideKeyManager(@ApplicationContext context: Context): KeyManager =
        KeyManager(context)

    /** BiometricPrompt 封装单例（§7.4；不持有 Activity 长引用，见 [BiometricHelper] 类注释）。 */
    @Provides
    @Singleton
    fun provideBiometricHelper(@ApplicationContext context: Context): BiometricHelper =
        BiometricHelper(context)

    /**
     * 应用锁控制器单例（§7.5/§9；持 Gate + KeyManager + SettingsDataStore + BiometricHelper）。
     * `@ApplicationScope` 由 [DispatcherModule] 提供（闲置重锁计时宿主，SupervisorJob + Main）。
     */
    @Provides
    @Singleton
    fun provideAppLockController(
        databaseGate: DatabaseGate,
        keyManager: KeyManager,
        settingsDataStore: SettingsDataStore,
        biometricHelper: BiometricHelper,
        @ApplicationScope applicationScope: CoroutineScope,
    ): AppLockController = AppLockController(
        databaseGate,
        keyManager,
        settingsDataStore,
        biometricHelper,
        applicationScope,
    )
}
