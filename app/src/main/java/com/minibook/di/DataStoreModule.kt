package com.minibook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.data.local.datastore.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/** 设置项 Preferences DataStore（进程内单例，name="settings"）。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 当前账本 Preferences DataStore（进程内单例，name="ledger"）。 */
private val Context.ledgerDataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger")

/**
 * DataStore 装配模块（TASK-005）。
 *
 * 提供两个独立 Preferences DataStore（datastore-preferences 1.1.1，DEC-001 锁定），
 * 并以 `@Named("settings")` / `@Named("ledger")` 限定符区分，避免 Hilt 对两个
 * `DataStore<Preferences>` 绑定产生歧义；SettingsDataStore / LedgerDataStore 由本模块
 * `@Provides` 构造注入对应 DataStore（ARCHITECTURE §5.1 DataStoreModule 行）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @Named("settings")
    fun provideSettingsPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    @Named("ledger")
    fun provideLedgerPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.ledgerDataStore

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @Named("settings") dataStore: DataStore<Preferences>,
    ): SettingsDataStore = SettingsDataStore(dataStore)

    @Provides
    @Singleton
    fun provideLedgerDataStore(
        @Named("ledger") dataStore: DataStore<Preferences>,
    ): LedgerDataStore = LedgerDataStore(dataStore)
}
