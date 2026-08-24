package com.minibook.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.minibook.data.local.datastore.LedgerDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 内存版 [DataStore]<[Preferences]>，供 JVM 单测直注 [LedgerDataStore]。
 *
 * 避免依赖 `datastore-preferences-core` 的文件/临时目录与真实
 * `PreferenceDataStoreFactory`——本替身仅实现 `data`（[MutableStateFlow] 读）与
 * `updateData`（写），满足 [androidx.datastore.preferences.core.edit] 扩展所需的契约；
 * 使 [LedgerDataStore] 的 `currentLedgerId: Flow<String>` 在 JVM 上可观察、可写入
 * （切账本经 `setCurrentLedgerId` 即时生效，供 `flatMapLatest` 重订阅验证）。
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {

    private val state = MutableStateFlow<Preferences>(preferencesOf())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * 构造一个无当前账本（空串，Bootstrap 前）的 [LedgerDataStore]。
 *
 * 测试内如需指定当前账本，在 `runTest` 中调用
 * `dataStore.setCurrentLedgerId("ledger-1")`（走真实公开 API，内部 key 私有不感知）。
 */
fun fakeLedgerDataStore(): LedgerDataStore = LedgerDataStore(InMemoryPreferencesDataStore())

/**
 * 构造一个当前账本已设为 [ledgerId] 的 [LedgerDataStore]（等价先建再
 * `setCurrentLedgerId`），供 observe 路径 UseCase 单测直注。suspend：走真实 API。
 */
suspend fun fakeLedgerDataStore(ledgerId: String): LedgerDataStore {
    val store = LedgerDataStore(InMemoryPreferencesDataStore())
    store.setCurrentLedgerId(ledgerId)
    return store
}
