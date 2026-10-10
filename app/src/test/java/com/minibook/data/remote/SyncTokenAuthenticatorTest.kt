package com.minibook.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.minibook.testutil.FakeSecretCipher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 令牌编排：**401 → 刷新一次 → 重试**，以及「刷新失败时哪些情况才清账号态」（TASK-065 验收点）。
 */
class SyncTokenAuthenticatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var store: SyncAuthStore

    @Before
    fun setUp() {
        store = SyncAuthStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tempFolder.root, "sync.preferences_pb") },
            ),
            FakeSecretCipher(),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun login(expiresInSeconds: Long = 1800) {
        store.saveSession(
            baseUrl = "http://192.168.1.5:8000",
            userId = "u-1",
            username = "alice",
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresInSeconds = expiresInSeconds,
        )
    }

    private fun pair(access: String = "access-2", refresh: String = "refresh-2") = SyncTokenPairDto(
        accessToken = access,
        refreshToken = refresh,
        expiresIn = 1800,
        user = SyncUserDto(userId = "u-1", username = "alice"),
    )

    @Test
    fun `令牌过期：只刷新一次并重试，新令牌落盘`() = runTest {
        login()
        var refreshCalls = 0
        var attempts = 0
        val authenticator = SyncTokenAuthenticator(store) { _, _ ->
            refreshCalls++
            pair()
        }

        val result = authenticator.withToken { session ->
            attempts++
            if (attempts == 1) throw SyncApiException(SyncErrorCode.TOKEN_EXPIRED, "expired")
            session.accessToken
        }

        assertEquals("access-2", result)
        assertEquals(2, attempts)
        assertEquals(1, refreshCalls)
        assertEquals("access-2", store.accessToken())
        assertEquals("refresh-2", store.refreshToken())
    }

    @Test
    fun `本地令牌已临近过期：调用前就静默刷新，不浪费一次 401`() = runTest {
        login(expiresInSeconds = 0)
        var refreshCalls = 0
        var attempts = 0
        val authenticator = SyncTokenAuthenticator(store) { _, _ ->
            refreshCalls++
            pair()
        }

        val used = authenticator.withToken { session ->
            attempts++
            session.accessToken
        }

        assertEquals("access-2", used)
        assertEquals(1, attempts)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `未登录：直接 NOT_LOGGED_IN，不发任何请求`() = runTest {
        var refreshCalls = 0
        val authenticator = SyncTokenAuthenticator(store) { _, _ ->
            refreshCalls++
            pair()
        }

        val error = runCatching { authenticator.withToken { it.accessToken } }.exceptionOrNull()

        assertTrue(error is SyncApiException)
        assertEquals(SyncErrorCode.NOT_LOGGED_IN, (error as SyncApiException).code)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `业务错误不触发刷新`() = runTest {
        login()
        var refreshCalls = 0
        val authenticator = SyncTokenAuthenticator(store) { _, _ ->
            refreshCalls++
            pair()
        }

        val error = runCatching {
            authenticator.withToken { throw SyncApiException(SyncErrorCode.VALIDATION_ERROR, "bad") }
        }.exceptionOrNull() as SyncApiException

        assertEquals(SyncErrorCode.VALIDATION_ERROR, error.code)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `刷新遇到网络故障：保留账号态，可重试`() = runTest {
        login(expiresInSeconds = 0)
        val authenticator = SyncTokenAuthenticator(store) { _, _ ->
            throw SyncApiException(SyncErrorCode.NETWORK_ERROR, "timeout")
        }

        val error = runCatching { authenticator.withToken { it.accessToken } }.exceptionOrNull()
            as SyncApiException

        assertEquals(SyncErrorCode.NETWORK_ERROR, error.code)
        assertTrue("网络故障不该清账号", store.current().isLoggedIn)
        assertEquals("refresh-1", store.refreshToken())
    }

    @Test
    fun `刷新令牌已撤销：清账号态并提示重新登录`() = runTest {
        login(expiresInSeconds = 0)
        val authenticator = SyncTokenAuthenticator(store) { _, _ ->
            throw SyncApiException(SyncErrorCode.TOKEN_REVOKED, "revoked")
        }

        val error = runCatching { authenticator.withToken { it.accessToken } }.exceptionOrNull()
            as SyncApiException

        assertEquals(SyncErrorCode.TOKEN_REVOKED, error.code)
        assertFalse(store.current().isLoggedIn)
        assertEquals(null, store.accessToken())
    }
}
