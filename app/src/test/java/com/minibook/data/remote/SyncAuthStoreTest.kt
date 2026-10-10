package com.minibook.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.minibook.data.local.security.SecretCipher
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 云同步本地状态（TASK-065 验收点：令牌经 Keystore 包裹后落盘、断言非明文）。
 *
 * 真实现走 Android Keystore（JVM 不可用），此处注入 [FakeSecretCipher]；
 * DataStore 用真实现 + 临时文件，因此**真的读一遍落盘字节**来断言令牌非明文。
 */
class SyncAuthStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(tempFolder.root, "sync.preferences_pb")
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore(cipher: SecretCipher = FakeSecretCipher()): SyncAuthStore =
        SyncAuthStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            cipher,
        )

    private fun rawFileText(): String = if (file.exists()) {
        String(file.readBytes(), Charsets.ISO_8859_1)
    } else {
        ""
    }

    @Test
    fun `保存会话：可读回令牌，且落盘字节里没有明文令牌`() = runTest {
        val store = newStore()

        store.saveSession(
            baseUrl = "http://192.168.1.5:8000",
            userId = "u-1",
            username = "alice",
            accessToken = "ACCESS-PLAINTEXT-TOKEN",
            refreshToken = "REFRESH-PLAINTEXT-TOKEN",
            expiresInSeconds = 1800,
        )

        val state = store.current()
        assertTrue(state.isLoggedIn)
        assertEquals("alice", state.username)
        assertEquals("http://192.168.1.5:8000", state.baseUrl)
        assertEquals("ACCESS-PLAINTEXT-TOKEN", store.accessToken())
        assertEquals("REFRESH-PLAINTEXT-TOKEN", store.refreshToken())

        // DEC-034：令牌绝不明文落盘
        val raw = rawFileText()
        assertFalse(raw.contains("ACCESS-PLAINTEXT-TOKEN"))
        assertFalse(raw.contains("REFRESH-PLAINTEXT-TOKEN"))
        assertTrue(raw.contains("enc:")) // 落盘的是密文
        // 非敏感项按口径明文存
        assertTrue(raw.contains("alice"))
    }

    @Test
    fun `令牌临近过期：到期前 60 秒内即视为该刷新`() = runTest {
        val store = newStore()
        store.saveSession("http://x", "u", "n", "a", "r", expiresInSeconds = 1800)
        assertFalse(store.isAccessExpiring())

        store.updateTokens("a2", "r2", expiresInSeconds = 0)
        assertTrue(store.isAccessExpiring())
        assertEquals("a2", store.accessToken())
    }

    @Test
    fun `清除会话：令牌与账号清空，但地址 设备号 游标保留`() = runTest {
        val store = newStore()
        store.saveSession("http://x", "u-1", "alice", "a", "r", expiresInSeconds = 1800)
        val deviceId = store.deviceId()
        store.setServerCursor(42)
        store.setLastPushedAt("2026-10-10T06:00:00.000000000Z")

        store.clearSession()

        val state = store.current()
        assertFalse(state.isLoggedIn)
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        // 重新登录即可续传：这些不该被登出清掉
        assertEquals("http://x", state.baseUrl)
        assertEquals(deviceId, state.deviceId)
        assertEquals(42L, state.serverCursor)
        assertEquals("2026-10-10T06:00:00.000000000Z", state.lastPushedAt)
    }

    @Test
    fun `设备号：首次生成后保持稳定`() = runTest {
        val store = newStore()
        val first = store.deviceId()
        assertTrue(first.isNotBlank())
        assertEquals(first, store.deviceId())
        assertEquals(first, store.current().deviceId)
    }

    @Test
    fun `游标与同步结果落盘`() = runTest {
        val store = newStore()
        store.setServerCursor(7)
        store.setLastPushedAt("2026-10-10T07:00:00.000000000Z")
        store.setLastSyncResult("2026-10-10T07:01:00.000000000Z", "UPLOAD", conflictCount = 2)

        val state = store.current()
        assertEquals(7L, state.serverCursor)
        assertEquals("2026-10-10T07:00:00.000000000Z", state.lastPushedAt)
        assertEquals("2026-10-10T07:01:00.000000000Z", state.lastSyncAt)
        assertEquals("UPLOAD", state.lastSyncMode)
        assertEquals(2, state.conflictCount)
    }

    @Test
    fun `令牌解密失败：按未登录处理，不抛异常`() = runTest {
        val store = newStore(cipher = object : SecretCipher {
            override fun encrypt(plaintext: String): String = "broken-blob"
            override fun decrypt(blob: String): String = throw IllegalStateException("Keystore key 失效")
        })

        store.saveSession("http://x", "u-1", "alice", "a", "r", expiresInSeconds = 1800)

        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertFalse(store.current().isLoggedIn) // hasTokens=false ⇒ 视为未登录
    }
}
