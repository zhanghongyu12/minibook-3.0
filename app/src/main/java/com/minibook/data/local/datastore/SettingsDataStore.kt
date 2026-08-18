package com.minibook.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 设置项 DataStore（Preferences DataStore，Hilt 单例，见 di/DataStoreModule）。
 *
 * 覆盖（ARCHITECTURE §9 / §11、TASK-027 设置页）：
 * - 暗色模式三态（默认 [DarkModeSetting.SYSTEM] 跟随系统）；
 * - 应用锁开关（默认关）；
 * - 锁闲置超时五档（默认 [LockTimeout.ONE_MINUTE]）；
 * - 加密态只读标记（SQLCipher + Keystore 已启用，F-10 架构级默认不可关）。
 *
 * 全部读写经 Preferences DataStore，读侧暴露为 `Flow`，供 UI `collectAsState`
 * 软切换（ARCHITECTURE §11）；写侧为 `suspend` 即时持久化。
 */
class SettingsDataStore(
    private val dataStore: DataStore<Preferences>,
) {

    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val LOCK_TIMEOUT = stringPreferencesKey("lock_timeout")
    }

    /** 暗色模式三态，无值默认 [DarkModeSetting.SYSTEM]（跟随系统）。 */
    val darkMode: Flow<DarkModeSetting> = dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE]?.let(DarkModeSetting::fromStorage) ?: DarkModeSetting.SYSTEM
    }

    /** 应用锁开关，无值默认关（false）。 */
    val appLockEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_ENABLED] ?: false
    }

    /** 锁闲置超时五档，无值默认 [LockTimeout.ONE_MINUTE]（1 分钟）。 */
    val lockTimeout: Flow<LockTimeout> = dataStore.data.map { prefs ->
        prefs[Keys.LOCK_TIMEOUT]?.let(LockTimeout::fromStorage) ?: LockTimeout.ONE_MINUTE
    }

    /**
     * 加密态只读标记：数据库整体加密（SQLCipher + Keystore）为架构级默认（F-10，
     * DEC-002 / D-1「锁可关，DB 仍加密」），设置页只读展示、不可关闭，故恒为 true。
     */
    val encryptionEnabled: Flow<Boolean> = flowOf(true)

    /** 写暗色模式（软切换，立即生效）。 */
    suspend fun setDarkMode(setting: DarkModeSetting) {
        dataStore.edit { it[Keys.DARK_MODE] = setting.name }
    }

    /** 写应用锁开关。 */
    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    /** 写锁闲置超时档位。 */
    suspend fun setLockTimeout(timeout: LockTimeout) {
        dataStore.edit { it[Keys.LOCK_TIMEOUT] = timeout.name }
    }
}
