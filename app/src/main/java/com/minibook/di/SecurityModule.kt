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
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 安全层装配（ARCHITECTURE §5.1、TASK-004 + TASK-017）。
 *
 * 提供：
 * - [KeyManager]：Keystore 双别名 + master key 包裹/解包（TASK-004）。
 * - [BiometricHelper]：BiometricPrompt 封装（§7.4），@Singleton。
 * - [AppLockController]：锁状态机 + 闲置超时重锁（§7.5/§9），@Singleton。
 * - [ApplicationScope] 应用级 [CoroutineScope]：闲置重锁计时宿主。
 *
 * 推迟项（编排者裁定，本期不实现）：
 * - MiniBookApp 锁生命周期观察者接入 → TASK-018（[AppLockController.onAppBackground]/
 *   [AppLockController.onAppForeground] 已就绪，仅差在 Application 注册接线）。
 * - ui/lock/LockState 锁状态模型 → TASK-022（本期 [AppLockController.isLocked] 以
 *   `StateFlow<Boolean>` 广播）。
 * - `@ApplicationScope` 正式归属 DispatcherModule（ARCHITECTURE §5.1 DispatcherModule 行）；
 *   因 DispatcherModule 属 TASK-018，本期暂驻本模块提供，TASK-018 收口时应迁走此绑定与
 *   本文件顶部 [ApplicationScope] 声明。
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

    /** 应用锁控制器单例（§7.5/§9；持 Gate + KeyManager + SettingsDataStore + BiometricHelper）。 */
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

    /**
     * 应用级协程作用域（闲置重锁计时宿主，SupervisorJob + Main）。
     * 暂驻本模块；TASK-018 DispatcherModule 收口时迁移至该模块（ARCHITECTURE §5.1）。
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
}

/**
 * 应用级协程作用域限定符（ARCHITECTURE §5.1/§5.2）。
 *
 * TASK-017 暂由 [SecurityModule] 提供；TASK-018 DispatcherModule 收口后应迁移至该模块，
 * 与 `@IoDispatcher`/`@DefaultDispatcher`/`@MainDispatcher` 并列。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
