package com.minibook.data.remote

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minibook.data.local.security.KeystoreSecretCipher
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「App 能登录」的**设备级**证据（TASK-071）：真 Keystore 包裹 + 真 DataStore 落盘 + 读回。
 *
 * 不走网络：若这一段失败，问题就在本机密钥/存储，而不是服务端 —— 这正是把「登录坏了」
 * 拆成两半来定位的意义（另一半是 HTTP，由 `SyncDeviceE2ETest` 覆盖）。
 */
@RunWith(AndroidJUnit4::class)
class SyncAuthStoreDeviceTest {

    @Test
    fun keystoreWrap_roundTrip_works() {
        val cipher = KeystoreSecretCipher()
        val blob = cipher.encrypt("hello-token-device")
        assertEquals("hello-token-device", cipher.decrypt(blob))
    }

    @Test
    fun saveSession_persists_andReadsBack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "sync-auth-test").apply { mkdirs() }
        val file = File(dir, "sync.preferences_pb")
        if (file.exists()) file.delete()

        val store = SyncAuthStore(
            PreferenceDataStoreFactory.create(produceFile = { file }),
            KeystoreSecretCipher(),
        )

        store.saveSession(
            baseUrl = "http://127.0.0.1:8000",
            userId = "u-device-1",
            username = "device-tester",
            accessToken = "ACCESS-TOKEN-DEVICE-123456",
            refreshToken = "REFRESH-TOKEN-DEVICE-123456",
            expiresInSeconds = 1800,
        )

        val state = store.state.first()
        assertTrue("登录态必须落盘且可读回（这就是「登录没反应」的根因位）", state.isLoggedIn)
        assertEquals("device-tester", state.username)
        assertEquals("ACCESS-TOKEN-DEVICE-123456", store.accessToken())
    }
}
