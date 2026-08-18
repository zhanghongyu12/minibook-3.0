package com.minibook.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 当前账本 DataStore（Preferences DataStore，Hilt 单例，见 di/DataStoreModule）。
 *
 * ARCHITECTURE §8.2 / §8.3：`currentLedgerId` 为进程级唯一状态，存 Preferences
 * DataStore 即时同步；切换为纯状态写（无数据搬移），写后立刻对全部
 * `flatMapLatest` Flow 生效（切账本自动重订阅）。
 *
 * 初始为空字符串，等待 TASK-011 BootstrapUseCase 写入默认账本 ID（§8.4）。
 */
class LedgerDataStore(
    private val dataStore: DataStore<Preferences>,
) {

    companion object {
        private val KEY_CURRENT_LEDGER_ID = stringPreferencesKey("current_ledger_id")
    }

    /** 当前账本 ID，即时同步；无值时空字符串（待 Bootstrap 写入）。 */
    val currentLedgerId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_LEDGER_ID].orEmpty()
    }

    /** 切换当前账本（纯状态写，ARCHITECTURE §8.3）。 */
    suspend fun setCurrentLedgerId(ledgerId: String) {
        dataStore.edit { it[KEY_CURRENT_LEDGER_ID] = ledgerId }
    }
}
