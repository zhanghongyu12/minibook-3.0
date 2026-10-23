package com.minibook.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.minibook.data.local.security.SecretCipher
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 云同步本地状态（DataStore，`name = "sync"`；见 di/SyncModule）。
 *
 * 口径（`docs/14_cloud_sync.md §4.2`）：
 * - 非敏感（base_url / username / user_id / device_id / 游标 / 上次同步信息）**明文**存；
 * - `access_token` / `refresh_token` 经 [SecretCipher]（Android Keystore）包裹后存 Base64；
 * - 令牌解密失败（如换机恢复导致 Keystore key 失效）**不崩**：按「无令牌」处理并清账号态。
 *
 * `server_cursor` / `last_pushed_at` 是「失败不推进」的两根指针（§4.7 第 4 条），
 * 故只允许在整轮成功之后由 UseCase 写入。
 */
class SyncAuthStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secretCipher: SecretCipher,
) {

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_NICKNAME = stringPreferencesKey("nickname")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token_enc")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token_enc")
        val KEY_ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at_ms")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
        val KEY_SERVER_CURSOR = longPreferencesKey("server_cursor")
        val KEY_LAST_PUSHED_AT = stringPreferencesKey("last_pushed_at")
        val KEY_LAST_SYNC_AT = stringPreferencesKey("last_sync_at")
        val KEY_LAST_SYNC_MODE = stringPreferencesKey("last_sync_mode")
        val KEY_CONFLICT_COUNT = intPreferencesKey("conflict_count")

        /** 令牌提前刷新窗口：到期前 60 秒内即视为「该刷新了」。 */
        const val REFRESH_WINDOW_MS = 60_000L
    }

    /** UI 观察用状态（不含令牌明文）。 */
    val state: Flow<SyncAccountState> = dataStore.data.map { it.toState() }

    suspend fun current(): SyncAccountState = state.first()

    suspend fun isLoggedIn(): Boolean = current().isLoggedIn

    suspend fun setBaseUrl(baseUrl: String) {
        dataStore.edit { it[KEY_BASE_URL] = baseUrl }
    }

    suspend fun baseUrl(): String = current().baseUrl

    /**
     * 自动同步开关（**默认开**，DEC-038）：已登录时「打开 / 回到 App」静默跑一次双向同步。
     * 关掉后只剩手动三模式。开关本身不是敏感信息，明文存。
     */
    suspend fun autoSyncEnabled(): Boolean = dataStore.data.first()[KEY_AUTO_SYNC] ?: true

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }

    /** 登录/注册成功后落盘会话（令牌加密）。 */
    suspend fun saveSession(
        baseUrl: String,
        userId: String,
        username: String,
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USERNAME] = username
            prefs[KEY_ACCESS_TOKEN] = secretCipher.encrypt(accessToken)
            prefs[KEY_REFRESH_TOKEN] = secretCipher.encrypt(refreshToken)
            prefs[KEY_ACCESS_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000L
        }
        // 写完**必须能读回**才算登录成功：否则会出现「服务端 200、本地却没登录」的静默失败
        // ——用户看到的就是「点了登录没反应」。宁可在这里抛一条能读懂的错。
        check(current().isLoggedIn) { "登录信息未能写入本地（令牌写入后不可读，请重试）" }
    }

    /** 令牌轮换后只更新令牌与到期时间（不动账号与游标）。 */
    suspend fun updateTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = secretCipher.encrypt(accessToken)
            prefs[KEY_REFRESH_TOKEN] = secretCipher.encrypt(refreshToken)
            prefs[KEY_ACCESS_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000L
        }
    }

    suspend fun accessToken(): String? = decryptOrNull(dataStore.data.first()[KEY_ACCESS_TOKEN])

    suspend fun refreshToken(): String? = decryptOrNull(dataStore.data.first()[KEY_REFRESH_TOKEN])

    /** 访问令牌是否临近/已经过期（无令牌视为「该刷新」，由调用方先判断是否已登录）。 */
    suspend fun isAccessExpiring(): Boolean {
        val expiresAt = dataStore.data.first()[KEY_ACCESS_EXPIRES_AT] ?: 0L
        if (expiresAt <= 0L) return true
        return expiresAt - System.currentTimeMillis() <= REFRESH_WINDOW_MS
    }

    /**
     * 清除会话（令牌 + 账号信息），**保留** `base_url` / `device_id` / 游标与冲突计数：
     * 用户重新登录即可继续，不会因为一次登出就把同步进度清零。
     */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_NICKNAME)
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_ACCESS_EXPIRES_AT)
        }
    }

    /**
     * 缓存服务端昵称（**展示用副本**，非权威数据）。
     *
     * 用途：首页顶部「登录信息」行要在**不发起网络请求**的前提下显示昵称
     * （2026-10-13 用户要求：未登录显示登录链接、已登录显示昵称）。
     * 权威值永远在服务端（`GET /me` 的 `nickname`）——本处只是把它缓存下来供离线展示；
     * 每次成功拉到资料（账号页进入 / 保存资料）都会覆盖写一次。
     */
    suspend fun updateNickname(nickname: String) {
        dataStore.edit { it[KEY_NICKNAME] = nickname }
    }

    /** 设备 ID：首次调用生成 UUID 并持久化（随请求上报，用于服务端设备列表）。 */
    suspend fun deviceId(): String {
        val existing = dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrEmpty()) return existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    suspend fun serverCursor(): Long = current().serverCursor

    /** **仅在一轮 push 全部成功后**调用（失败不推进，§4.7）。 */
    suspend fun setLastPushedAt(isoTimestamp: String) {
        dataStore.edit { it[KEY_LAST_PUSHED_AT] = isoTimestamp }
    }

    /** **仅在一轮 pull 全部成功后**调用。 */
    suspend fun setServerCursor(cursor: Long) {
        dataStore.edit { it[KEY_SERVER_CURSOR] = cursor }
    }

    /** 记录一次同步结果（UI 状态行）。 */
    suspend fun setLastSyncResult(atIso: String, mode: String, conflictCount: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_AT] = atIso
            prefs[KEY_LAST_SYNC_MODE] = mode
            prefs[KEY_CONFLICT_COUNT] = conflictCount
        }
    }

    suspend fun setConflictCount(count: Int) {
        dataStore.edit { it[KEY_CONFLICT_COUNT] = count }
    }

    private fun decryptOrNull(blob: String?): String? {
        if (blob.isNullOrEmpty()) return null
        return try {
            secretCipher.decrypt(blob)
        } catch (e: Exception) {
            null // Keystore key 失效/密文损坏 ⇒ 当作未登录，绝不因此崩溃
        }
    }

    private fun Preferences.toState(): SyncAccountState = SyncAccountState(
        baseUrl = this[KEY_BASE_URL].orEmpty(),
        username = this[KEY_USERNAME].orEmpty(),
        userId = this[KEY_USER_ID].orEmpty(),
        nickname = this[KEY_NICKNAME].orEmpty(),
        deviceId = this[KEY_DEVICE_ID].orEmpty(),
        serverCursor = this[KEY_SERVER_CURSOR] ?: 0L,
        lastPushedAt = this[KEY_LAST_PUSHED_AT].orEmpty(),
        lastSyncAt = this[KEY_LAST_SYNC_AT].orEmpty(),
        lastSyncMode = this[KEY_LAST_SYNC_MODE].orEmpty(),
        conflictCount = this[KEY_CONFLICT_COUNT] ?: 0,
        // 必须**真的解得开**才算有令牌：Keystore key 失效（换机恢复/清数据残留）时
        // 密文仍在，若只看「非空」会误报已登录，导致每次同步都撞 NOT_LOGGED_IN。
        hasTokens = decryptOrNull(this[KEY_ACCESS_TOKEN]) != null,
    )
}

/** 云账号与同步进度的只读快照。 */
data class SyncAccountState(
    val baseUrl: String = "",
    val username: String = "",
    val userId: String = "",
    /** 服务端昵称的本地缓存副本（可能为空；权威值在服务端）。 */
    val nickname: String = "",
    val deviceId: String = "",
    val serverCursor: Long = 0L,
    val lastPushedAt: String = "",
    val lastSyncAt: String = "",
    val lastSyncMode: String = "",
    val conflictCount: Int = 0,
    val hasTokens: Boolean = false,
) {
    /** 已登录 = 有 user_id 且本地有令牌。 */
    val isLoggedIn: Boolean get() = userId.isNotEmpty() && hasTokens

    /** 服务器地址是否已配置（未登录时也可先填地址）。 */
    val hasServer: Boolean get() = baseUrl.isNotEmpty()

    /**
     * 首页「登录信息」行要显示的名字：已登录 = 昵称优先、回退用户名；未登录 = null。
     *
     * 纯派生属性（无网络、无 IO），供首页在离线时也能立刻显示出名字。
     */
    val displayName: String? get() = when {
        !isLoggedIn -> null
        nickname.isNotBlank() -> nickname
        username.isNotBlank() -> username
        else -> null
    }
}
