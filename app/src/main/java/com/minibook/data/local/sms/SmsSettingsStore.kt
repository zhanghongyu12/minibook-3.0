package com.minibook.data.local.sms

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minibook.di.IoDispatcher
import com.minibook.domain.model.SmsNotifyMode
import com.minibook.domain.model.SmsSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.smsSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sms_settings",
)

/**
 * 短信账单开关存储（F-14 设计 §11.2）。
 *
 * 只存开关与水位，**不含任何短信内容**（DEC-026）。`lastWatermarkMs` 为 L3 增量补扫起点
 * （水位由读取层给出，写入时机由编排层决定）。
 */
@Singleton
class SmsSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val realtime = booleanPreferencesKey("realtime")
        val autoImport = booleanPreferencesKey("auto_import")
        val autoScan = booleanPreferencesKey("auto_scan")
        val notifyMode = stringPreferencesKey("notify_mode")
        val watermark = longPreferencesKey("watermark")

        /** 「未识别已看水位」：该时刻之前（含）的未识别短信在清单里已展示过 → 折叠为「此前已看过」。 */
        val unrecognizedAck = longPreferencesKey("unrecognized_ack_ms")
    }

    /** 开关组（Flow：设置页与接收器共用）。 */
    val settings: Flow<SmsSettings> = context.smsSettingsDataStore.data.map { prefs ->
        val defaults = SmsSettings()
        SmsSettings(
            enabled = prefs[Keys.enabled] ?: defaults.enabled,
            realtimeEnabled = prefs[Keys.realtime] ?: defaults.realtimeEnabled,
            autoImportEnabled = prefs[Keys.autoImport] ?: defaults.autoImportEnabled,
            autoScanEnabled = prefs[Keys.autoScan] ?: defaults.autoScanEnabled,
            notifyMode = SmsNotifyMode.fromValue(prefs[Keys.notifyMode]),
            lastWatermarkMs = prefs[Keys.watermark]?.takeIf { it > 0L },
        )
    }

    /** 读取一次（接收器用；避免长订阅）。 */
    suspend fun current(): SmsSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) = edit { it[Keys.enabled] = enabled }

    suspend fun setRealtimeEnabled(enabled: Boolean) = edit { it[Keys.realtime] = enabled }

    suspend fun setAutoImportEnabled(enabled: Boolean) = edit { it[Keys.autoImport] = enabled }

    suspend fun setAutoScanEnabled(enabled: Boolean) = edit { it[Keys.autoScan] = enabled }

    suspend fun setNotifyMode(mode: SmsNotifyMode) = edit { it[Keys.notifyMode] = mode.value }

    /** 推进水位（取更大值，防乱序回退）。 */
    suspend fun advanceWatermark(watermarkMs: Long) = edit { prefs ->
        val current = prefs[Keys.watermark] ?: 0L
        if (watermarkMs > current) prefs[Keys.watermark] = watermarkMs
    }

    /**
     * 「未识别已看水位」（2026-10-10）：清单页展示过扫描结果后调用，取更大值（防回退）。
     *
     * 只存一个时间戳 —— 不含短信内容/号码/发送方（DEC-026），也不影响导入口径。
     */
    suspend fun acknowledgeUnrecognized(upToMs: Long) = edit { prefs ->
        val current = prefs[Keys.unrecognizedAck] ?: 0L
        if (upToMs > current) prefs[Keys.unrecognizedAck] = upToMs
    }

    /** 读取「未识别已看水位」（未设置 = 0，表示从未展示过）。 */
    suspend fun unrecognizedAckMs(): Long = context.smsSettingsDataStore.data.first()[Keys.unrecognizedAck] ?: 0L

    private suspend fun edit(block: (MutablePreferences) -> Unit) =
        withContext(ioDispatcher) { context.smsSettingsDataStore.edit(block) }
}
