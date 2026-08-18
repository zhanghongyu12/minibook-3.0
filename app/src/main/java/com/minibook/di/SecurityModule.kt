package com.minibook.di

import android.content.Context
import com.minibook.data.local.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 安全层装配（ARCHITECTURE §5.1、TASK-004）。
 *
 * 本期只提供 [KeyManager]（@Singleton，Keystore 双别名 + master key 包裹/解包）。
 *
 * TASK-017 补全项（此处留 TODO，不实现）：
 * - `BiometricHelper`：BiometricPrompt 封装（`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`），
 *   成功回调返回 `cryptoObject.cipher`（ARCHITECTURE §7.4）；依赖主线程回调与
 *   `isDeviceSecure` 检测（DEC-003）。
 * - `AppLockController`：锁状态机 + 闲置计时重锁（ARCHITECTURE §7.5/§9），依赖
 *   `DatabaseGate` 与 `SettingsDataStore.appLockEnabled/lockTimeout`（TASK-005）。
 * - 锁设置读取占位：本期无独立提供者，设置读写由 TASK-005 SettingsDataStore 承担。
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /** Keystore 密钥管理单例（懒创建双别名，见 [KeyManager] 类注释）。 */
    @Provides
    @Singleton
    fun provideKeyManager(@ApplicationContext context: Context): KeyManager =
        KeyManager(context)

    // TODO(TASK-017): @Provides BiometricHelper / AppLockController / 锁设置读取。
}
