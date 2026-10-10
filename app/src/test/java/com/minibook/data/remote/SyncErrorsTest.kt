package com.minibook.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 错误码映射与面向用户文案（TASK-065 验收点：错误码映射）。 */
class SyncErrorsTest {

    @Test
    fun `服务端错误码逐字映射`() {
        assertEquals(SyncErrorCode.INVALID_CREDENTIALS, SyncErrorCode.fromWire("invalid_credentials"))
        assertEquals(SyncErrorCode.USERNAME_TAKEN, SyncErrorCode.fromWire("username_taken"))
        assertEquals(SyncErrorCode.VALIDATION_ERROR, SyncErrorCode.fromWire("validation_error"))
        assertEquals(SyncErrorCode.TOKEN_EXPIRED, SyncErrorCode.fromWire("token_expired"))
        assertEquals(SyncErrorCode.TOKEN_INVALID, SyncErrorCode.fromWire("token_invalid"))
        assertEquals(SyncErrorCode.TOKEN_REVOKED, SyncErrorCode.fromWire("token_revoked"))
        assertEquals(SyncErrorCode.NOT_FOUND, SyncErrorCode.fromWire("not_found"))
        assertEquals(SyncErrorCode.RATE_LIMITED, SyncErrorCode.fromWire("rate_limited"))
        assertEquals(SyncErrorCode.SERVER_ERROR, SyncErrorCode.fromWire("server_error"))
    }

    @Test
    fun `未知错误码归 UNKNOWN 而不抛异常（前向兼容）`() {
        assertEquals(SyncErrorCode.UNKNOWN, SyncErrorCode.fromWire("brand_new_code_from_future"))
        assertEquals(SyncErrorCode.UNKNOWN, SyncErrorCode.fromWire(null))
    }

    @Test
    fun `客户端自产码没有 wire 值，不会与服务端码混淆`() {
        assertTrue(SyncErrorCode.NETWORK_ERROR.wire == null)
        assertTrue(SyncErrorCode.CLEARTEXT_BLOCKED.wire == null)
        assertTrue(SyncErrorCode.INVALID_BASE_URL.wire == null)
        assertTrue(SyncErrorCode.NOT_LOGGED_IN.wire == null)
        // 用 null wire 反查不应命中任何客户端码
        assertEquals(SyncErrorCode.UNKNOWN, SyncErrorCode.fromWire(null))
    }

    @Test
    fun `可重试性：只有网络与临时服务端故障可重试`() {
        assertTrue(SyncApiException(SyncErrorCode.NETWORK_ERROR, "x").isRetryable)
        assertTrue(SyncApiException(SyncErrorCode.SERVER_ERROR, "x").isRetryable)
        assertTrue(SyncApiException(SyncErrorCode.RATE_LIMITED, "x").isRetryable)
        assertFalse(SyncApiException(SyncErrorCode.INVALID_CREDENTIALS, "x").isRetryable)
        assertFalse(SyncApiException(SyncErrorCode.VALIDATION_ERROR, "x").isRetryable)
        assertFalse(SyncApiException(SyncErrorCode.NOT_LOGGED_IN, "x").isRetryable)
    }

    @Test
    fun `用户文案：中文且含条目下标、不含账目明细`() {
        val validation = SyncApiException(SyncErrorCode.VALIDATION_ERROR, "内部原因", index = 2)
        assertTrue(validation.userMessage().contains("第 3 条"))

        val cleartext = SyncApiException(SyncErrorCode.CLEARTEXT_BLOCKED, "CLEARTEXT not permitted")
        assertTrue(cleartext.userMessage().contains("https"))

        val network = SyncApiException(SyncErrorCode.NETWORK_ERROR, "timeout")
        assertTrue(network.userMessage().contains("网络"))

        // 文案不得包含服务端内部 message（可能含备注等敏感内容）
        assertFalse(validation.userMessage().contains("内部原因"))
    }
}
