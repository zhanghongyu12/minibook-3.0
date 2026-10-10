package com.minibook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.minibook.data.local.security.KeystoreSecretCipher
import com.minibook.data.local.security.SecretCipher
import com.minibook.data.remote.SyncApi
import com.minibook.data.remote.SyncAuthStore
import com.minibook.data.remote.SyncRemote
import com.minibook.data.remote.SyncTokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** 云同步 Preferences DataStore（进程内单例，name="sync"；与 settings/ledger 分离）。 */
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

/**
 * 云同步装配（F-15，TASK-065）。
 *
 * 依赖口径（`docs/14_cloud_sync.md §5.1`）：**唯一新增三方依赖是 OkHttp**；
 * JSON 复用既有 kotlinx-serialization，状态复用既有 DataStore，令牌包裹复用 Keystore。
 *
 * OkHttp 实例全 App 共用一个（连接池复用、线程安全）；超时故意偏短：
 * 手动同步是用户在场操作，宁可快速失败并给出可读原因，也不要长时间转圈。
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncJson(): Json = Json {
        ignoreUnknownKeys = true // 服务端加字段不炸旧客户端
        explicitNulls = false // 不把 null 发出去（墓碑就该省 payload）
        encodeDefaults = true // deleted=false 也显式发送，避免两端默认值口径漂移
    }

    @Provides
    @Singleton
    fun provideSyncPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.syncDataStore

    @Provides
    @Singleton
    fun provideSecretCipher(): SecretCipher = KeystoreSecretCipher()

    @Provides
    @Singleton
    fun provideSyncAuthStore(
        dataStore: DataStore<Preferences>,
        secretCipher: SecretCipher,
    ): SyncAuthStore = SyncAuthStore(dataStore, secretCipher)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideSyncApi(
        client: OkHttpClient,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SyncApi = SyncApi(client, json, ioDispatcher)

    @Provides
    @Singleton
    fun provideSyncRemote(api: SyncApi): SyncRemote = api

    @Provides
    @Singleton
    fun provideSyncTokenAuthenticator(
        store: SyncAuthStore,
        remote: SyncRemote,
    ): SyncTokenAuthenticator = SyncTokenAuthenticator(store) { baseUrl, request ->
        remote.refresh(baseUrl, request)
    }
}
