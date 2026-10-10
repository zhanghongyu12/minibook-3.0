package com.minibook.data.local.sms

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minibook.di.IoDispatcher
import com.minibook.domain.model.SmsWhitelistEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 顶层委托：白名单条目以 `Set<String>` 形式存储（编解码见 [SmsWhitelistStoreCodec]）。 */
private val Context.smsWhitelistDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sms_whitelist",
)

/**
 * 短信白名单存储（F-14，设计 §3.8；DEC-025）。
 *
 * 只存**发送方号码 + 开关 + 确认计数 + 错误时间**，不含任何短信原文（DEC-026）。
 * DataStore（app 私有目录），无第三方依赖。
 */
@Singleton
class SmsWhitelistStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val key = stringSetPreferencesKey("entries")

    /** 读取全部条目（按 sender 升序）。 */
    suspend fun entries(): List<SmsWhitelistEntry> = withContext(ioDispatcher) {
        SmsWhitelistStoreCodec.decode(readRaw())
    }

    /** 读取单条。 */
    suspend fun find(sender: String): SmsWhitelistEntry? =
        entries().firstOrNull { it.sender == sender }

    /** 覆盖写入一条（不存在则新增）。 */
    suspend fun upsert(entry: SmsWhitelistEntry) = withContext(ioDispatcher) {
        if (entry.sender.isEmpty()) return@withContext
        writeAll(SmsWhitelistStoreCodec.decode(readRaw()).filterNot { it.sender == entry.sender } + entry)
    }

    /** 按发送方开关（设置页逐项开关；不改计数）。 */
    suspend fun setAutoEnabled(sender: String, enabled: Boolean) = withContext(ioDispatcher) {
        val all = SmsWhitelistStoreCodec.decode(readRaw()).toMutableList()
        val idx = all.indexOfFirst { it.sender == sender }
        when {
            idx >= 0 -> all[idx] = all[idx].copy(autoEnabled = enabled)
            enabled -> all += SmsWhitelistEntry(sender = sender, autoEnabled = true, confirmedCount = 0)
        }
        writeAll(all)
    }

    /** 清空（隐私：设置页「清空短信白名单」）。 */
    suspend fun clear() = withContext(ioDispatcher) {
        context.smsWhitelistDataStore.edit { it.remove(key) }
    }

    private suspend fun readRaw(): Set<String> = context.smsWhitelistDataStore.data.first()[key].orEmpty()

    private suspend fun writeAll(entries: List<SmsWhitelistEntry>) {
        context.smsWhitelistDataStore.edit { prefs ->
            prefs[key] = entries.map(SmsWhitelistStoreCodec::encode).toSet()
        }
    }
}
