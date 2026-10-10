package com.minibook.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTO 与 `server/app/schemas.py` 的字段口径一致性（TASK-065 验收点：序列化/反序列化）。
 *
 * 重点盯三件事：
 * 1. **信封是 snake_case**、payload 内是原样 camelCase；
 * 2. 服务端**新增字段**不能炸旧客户端（`ignoreUnknownKeys`）；
 * 3. `null` 不发出去、`deleted=false` 要显式发（两端默认值口径一致）。
 */
class SyncDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `push 请求：信封 snake_case、payload 原样 camelCase`() {
        val payload = buildJsonObject {
            put("id", "tx-1")
            put("ledgerId", "led-1")
            put("amount", 12345)
            put("updatedAt", "2026-10-10T06:05:00.123456789Z")
        }
        val request = SyncPushRequest(
            deviceId = "dev-1",
            items = listOf(
                SyncItemDto(
                    entityType = "transaction",
                    entityId = "tx-1",
                    ledgerId = "led-1",
                    updatedAt = "2026-10-10T06:05:00.123456789Z",
                    deleted = false,
                    payload = payload,
                ),
            ),
        )

        val text = json.encodeToString(SyncPushRequest.serializer(), request)

        assertTrue(text.contains("\"entity_type\":\"transaction\""))
        assertTrue(text.contains("\"entity_id\":\"tx-1\""))
        assertTrue(text.contains("\"ledger_id\":\"led-1\""))
        assertTrue(text.contains("\"updated_at\":\"2026-10-10T06:05:00.123456789Z\""))
        assertTrue(text.contains("\"device_id\":\"dev-1\""))
        // payload 内部不做任何改名
        assertTrue(text.contains("\"ledgerId\":\"led-1\""))
        assertTrue(text.contains("\"amount\":12345"))
        assertTrue(text.contains("\"deleted\":false"))
        // 纳秒时间戳必须原样保留（服务端按原串回传，客户端据此判 no-op）
        assertFalse(text.contains("+00:00"))
    }

    @Test
    fun `墓碑：不带 payload 也能编码，deleted 为 true`() {
        val item = SyncItemDto(
            entityType = "transaction",
            entityId = "tx-9",
            ledgerId = "led-1",
            updatedAt = "2026-10-10T07:00:00.000000000Z",
            deleted = true,
            payload = null,
        )
        val text = json.encodeToString(SyncItemDto.serializer(), item)
        assertTrue(text.contains("\"deleted\":true"))
        assertFalse(text.contains("payload"))
    }

    @Test
    fun `push 响应：解析 applied skipped conflicts`() {
        val text = """
            {"applied":2,"skipped":1,"conflicts":[{"entity_type":"transaction","entity_id":"tx-2",
            "reason":"server_newer","server":{"entity_type":"transaction","entity_id":"tx-2",
            "ledger_id":"led-1","updated_at":"2026-10-10T09:00:00.000000000Z","deleted":false,
            "payload":{"id":"tx-2","amount":200},"server_seq":7}}],"server_seq":7}
        """.trimIndent()

        val response = json.decodeFromString(SyncPushResponseDto.serializer(), text)

        assertEquals(2, response.applied)
        assertEquals(1, response.skipped)
        assertEquals(7L, response.serverSeq)
        assertEquals(1, response.conflicts.size)
        val conflict = response.conflicts.first()
        assertEquals("server_newer", conflict.reason)
        assertEquals("tx-2", conflict.server.entityId)
        assertEquals(7L, conflict.server.serverSeq)
        assertEquals("200", conflict.server.payload?.get("amount").toString())
    }

    @Test
    fun `pull 响应：items next_seq has_more 与墓碑条目`() {
        val text = """
            {"items":[{"entity_type":"ledger","entity_id":"led-1","ledger_id":"led-1",
            "updated_at":"2026-10-10T06:00:00.000000000Z","deleted":true,"payload":null,"server_seq":3}],
            "next_seq":3,"has_more":false}
        """.trimIndent()

        val response = json.decodeFromString(SyncPullResponseDto.serializer(), text)

        assertEquals(1, response.items.size)
        assertEquals(3L, response.nextSeq)
        assertFalse(response.hasMore)
        assertTrue(response.items.first().deleted)
        assertNull(response.items.first().payload)
    }

    @Test
    fun `status：计数求和即云端记录数`() {
        val text = """
            {"server_seq":5,"server_seq_global":9,
            "counts":{"transaction":3,"ledger":1},"server_time":"2026-10-10T07:00:00.000000Z"}
        """.trimIndent()

        val status = json.decodeFromString(SyncStatusDto.serializer(), text)

        assertEquals(5L, status.serverSeq)
        assertEquals(9L, status.serverSeqGlobal)
        assertEquals(4, status.totalCount)
    }

    @Test
    fun `服务端新增字段不影响旧客户端`() {
        val text = """
            {"access_token":"a","refresh_token":"r","token_type":"bearer","expires_in":1800,
            "user":{"user_id":"u1","username":"alice","nickname":"新字段"},
            "server_hint":"未来字段"}
        """.trimIndent()

        val pair = json.decodeFromString(SyncTokenPairDto.serializer(), text)

        assertEquals("a", pair.accessToken)
        assertEquals("u1", pair.user.userId)
        assertEquals(1800L, pair.expiresIn)
    }

    @Test
    fun `错误体：解析 code index entity_id`() {
        val text = """
            {"error":{"code":"validation_error","message":"第 1 条校验失败","index":1,"entity_id":"tx-2"}}
        """.trimIndent()

        val envelope = json.decodeFromString(SyncErrorEnvelopeDto.serializer(), text)

        assertEquals("validation_error", envelope.error.code)
        assertEquals(1, envelope.error.index)
        assertEquals("tx-2", envelope.error.entityId)
    }
}
