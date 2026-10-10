package com.minibook.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 服务器地址校验（含「正式包禁明文 HTTP」这条隐私/安全口径）。 */
class SyncUrlValidatorTest {

    @Test
    fun `调试包：未写 scheme 时补 http，去尾斜杠`() {
        assertEquals("http://192.168.1.5:8000", SyncUrlValidator.normalize("192.168.1.5:8000", debuggable = true))
        assertEquals("http://192.168.1.5:8000", SyncUrlValidator.normalize(" http://192.168.1.5:8000/ ", debuggable = true))
    }

    @Test
    fun `正式包：未写 scheme 时补 https`() {
        assertEquals(
            "https://sync.example.com",
            SyncUrlValidator.normalize("sync.example.com", debuggable = false),
        )
    }

    @Test
    fun `正式包填 http 直接拒绝并给出可读原因`() {
        val error = runCatching { SyncUrlValidator.normalize("http://192.168.1.5:8000", debuggable = false) }
            .exceptionOrNull() as SyncApiException
        assertEquals(SyncErrorCode.INVALID_BASE_URL, error.code)
        assertTrue(error.userMessage().contains("服务器地址无效"))
    }

    @Test
    fun `允许反向代理的路径前缀`() {
        assertEquals(
            "https://example.com/minibook",
            SyncUrlValidator.normalize("https://example.com/minibook/", debuggable = false),
        )
        assertEquals(
            "https://example.com/minibook/api/v1/me",
            SyncUrlValidator.endpoint("https://example.com/minibook", "/api/v1/me"),
        )
    }

    @Test
    fun `空地址 畸形主机名与越界端口被拒`() {
        listOf("", "   ", "http://", "://x", "192.168.1.5:99999", "http://a b:8000").forEach { raw ->
            val error = runCatching { SyncUrlValidator.normalize(raw, debuggable = true) }.exceptionOrNull()
            assertTrue("$raw 应被拒", error is SyncApiException)
        }
    }

    @Test
    fun `IPv6 字面量与常见端口被接受`() {
        assertEquals("http://[::1]:8000", SyncUrlValidator.normalize("[::1]:8000", debuggable = true))
        assertEquals(
            "http://192.168.1.5:49779",
            SyncUrlValidator.normalize("192.168.1.5:49779", debuggable = true),
        )
    }
}
