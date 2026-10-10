package com.minibook.data.remote

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * **真实服务端端到端**测试（F-15 / TASK-068 的「无设备」部分）。
 *
 * 与其它单测的区别：**它不打桩**——用的是生产代码路径（[SyncApi] 真 OkHttp + 真 DTO 序列化 +
 * 真错误码映射）去打真在跑的 API 服务（真 MySQL）。因此它能抓到单测抓不到的东西：
 * 字段白名单漂移、UTF-8 往返、`401` 归类、刷新轮换、墓碑下行、幂等 no-op。
 *
 * **默认跳过**（没配服务地址时 `Assume` 跳过，如实记 skipped 而非假装通过）。
 * 三种开法（任一即可，见类底部 [baseUrlFrom]）：
 * 1. 环境变量 `MINIBOOK_E2E_BASE_URL=http://127.0.0.1:8000`
 * 2. JVM 系统属性 `-Dminibook.e2e.baseUrl=...`
 * 3. 文件 `app/build/e2e-base-url.txt`（够用且不受 Gradle daemon 环境影响）
 *
 * 副作用：会在开发库留下一个 `e2e_*` 账号及其记录（服务端无删除 API）；收口时用 SQL 清理。
 */
class SyncApiE2ETest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val api = SyncApi(OkHttpClient(), json, Dispatchers.IO)

    @Test
    fun `注册登录推送拉取幂等墓碑与令牌轮换全链路`() = runBlocking {
        val base = baseUrlFrom()
        Assume.assumeTrue("未配置真实服务地址（MINIBOOK_E2E_BASE_URL），跳过端到端", base != null)
        val endpoint = base!!.trimEnd('/')

        val username = "e2e_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val password = "E2e!Passw0rd"

        // 1) 注册 → 登录 → me（同一账号、同一 userId）
        val registered = api.register(
            endpoint,
            SyncRegisterRequest(username = username, password = password, deviceId = DEVICE, deviceName = "jvm-e2e"),
        )
        assertEquals(username, registered.username)

        val tokens = api.login(
            endpoint,
            SyncLoginRequest(
                username = username,
                password = password,
                deviceId = DEVICE,
                deviceName = "jvm-e2e",
                platform = "android",
            ),
        )
        assertTrue("access_token 不应为空", tokens.accessToken.isNotBlank())
        assertTrue("refresh_token 不应为空", tokens.refreshToken.isNotBlank())
        assertEquals(registered.userId, tokens.user.userId)
        assertEquals(username, api.me(endpoint, tokens.accessToken).username)

        // 2) 推送 3 类实体（含中文 + emoji：4 字节 UTF-8 也不许坏）
        val ledgerId = UUID.randomUUID().toString()
        val categoryId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val chinese = "端到端中文描述：午饭 12.5 元 🍜"
        val stamp = "2026-10-10T07:30:00.000000000Z"

        val pushItems = listOf(
            SyncItemDto(
                entityType = "ledger",
                entityId = ledgerId,
                ledgerId = ledgerId,
                updatedAt = stamp,
                payload = buildJsonObject {
                    put("id", ledgerId)
                    put("name", "测试账本·中文")
                    put("icon", "wallet")
                    put("color", "#FF5722")
                    put("isDefault", true)
                    put("position", 0)
                    put("createdAt", stamp)
                    put("updatedAt", stamp)
                },
            ),
            SyncItemDto(
                entityType = "category",
                entityId = categoryId,
                ledgerId = ledgerId,
                updatedAt = stamp,
                payload = buildJsonObject {
                    put("id", categoryId)
                    put("ledgerId", ledgerId)
                    put("name", "餐饮")
                    put("icon", "restaurant")
                    put("color", "#4CAF50")
                    put("type", "expense")
                    put("isBuiltIn", false)
                    put("order", 1)
                    put("createdAt", stamp)
                    put("updatedAt", stamp)
                },
            ),
            SyncItemDto(
                entityType = "transaction",
                entityId = transactionId,
                ledgerId = ledgerId,
                updatedAt = stamp,
                payload = buildJsonObject {
                    put("id", transactionId)
                    put("ledgerId", ledgerId)
                    put("type", "expense")
                    put("amount", 1250)
                    put("categoryId", categoryId)
                    put("description", chinese)
                    put("date", "2026-10-10")
                    put("createdAt", stamp)
                    put("updatedAt", stamp)
                    put("syncStatus", "local")
                    put("externalId", JsonNull)
                    put("counterparty", JsonNull)
                    put("paymentMethod", JsonNull)
                    put("source", "manual")
                },
            ),
        )

        val pushed = api.push(
            endpoint,
            tokens.accessToken,
            SyncPushRequest(deviceId = DEVICE, items = pushItems),
        )
        assertEquals("三条都应被接受", 3, pushed.applied)
        assertTrue("首次推送不应有冲突", pushed.conflicts.isEmpty())
        assertTrue("server_seq 应前进", pushed.serverSeq > 0)

        // 3) 拉回：三类实体齐全，中文逐字一致（含 emoji 的 4 字节 UTF-8）
        val pulled = api.pull(endpoint, tokens.accessToken, sinceSeq = 0, limit = 200)
        val byId = pulled.items.associateBy { it.entityId }
        assertTrue("应能拉回账本", byId.containsKey(ledgerId))
        assertTrue("应能拉回分类", byId.containsKey(categoryId))
        assertTrue("应能拉回交易", byId.containsKey(transactionId))

        val pulledTx = byId.getValue(transactionId)
        assertEquals("updated_at 必须与客户端原样一致", stamp, pulledTx.updatedAt)
        assertEquals(false, pulledTx.deleted)
        val description = pulledTx.payload!!.getValue("description").jsonPrimitive.content
        assertEquals("中文与 emoji 必须逐字往返", chinese, description)
        assertEquals("UTF-8 字节数须与源串一致", chinese.toByteArray(Charsets.UTF_8).size, description.toByteArray(Charsets.UTF_8).size)
        assertEquals(1250, pulledTx.payload.getValue("amount").jsonPrimitive.content.toInt())

        // 4) 幂等：同样的 updatedAt 再推一次 → 不涨计数、不冲突
        val again = api.push(
            endpoint,
            tokens.accessToken,
            SyncPushRequest(deviceId = DEVICE, items = pushItems),
        )
        assertEquals("重复推送必须 no-op", 0, again.applied)
        assertEquals("重复推送不算冲突", 0, again.conflicts.size)
        assertEquals("server_seq 不应因 no-op 前进", pushed.serverSeq, again.serverSeq)

        // 5) 墓碑：删除交易（更新的 updatedAt + deleted=true + 无 payload）
        val deleteStamp = "2026-10-10T08:30:00.000000000Z"
        val deleted = api.push(
            endpoint,
            tokens.accessToken,
            SyncPushRequest(
                deviceId = DEVICE,
                items = listOf(
                    SyncItemDto(
                        entityType = "transaction",
                        entityId = transactionId,
                        ledgerId = ledgerId,
                        updatedAt = deleteStamp,
                        deleted = true,
                        payload = null,
                    ),
                ),
            ),
        )
        assertEquals("墓碑应被接受", 1, deleted.applied)

        val afterDelete = api.pull(endpoint, tokens.accessToken, sinceSeq = pushed.serverSeq, limit = 200)
        val tombstone = afterDelete.items.last { it.entityId == transactionId }
        assertTrue("下行必须是墓碑", tombstone.deleted)
        assertEquals(deleteStamp, tombstone.updatedAt)

        // 6) 状态计数（云端记录数 = 3，墓碑也占一条记录）
        val status = api.status(endpoint, tokens.accessToken)
        assertEquals("云端应有 3 条记录", 3, status.totalCount)
        assertTrue(status.serverSeq >= deleted.serverSeq)

        // 7) 令牌轮换：refresh 出的新 access token 立即可用
        val refreshed = api.refresh(
            endpoint,
            SyncRefreshRequest(refreshToken = tokens.refreshToken, deviceId = DEVICE),
        )
        assertTrue("轮换后应拿到新的 access_token", refreshed.accessToken.isNotBlank())
        assertEquals(username, api.me(endpoint, refreshed.accessToken).username)

        // 8) 旧的 refresh token 已轮换（服务端撤销）→ 再用应被拒
        val reuse = runCatching {
            api.refresh(endpoint, SyncRefreshRequest(refreshToken = tokens.refreshToken, deviceId = DEVICE))
        }
        assertTrue("已轮换的 refresh token 必须失效", reuse.isFailure)

        // 9) 坏令牌 → 统一映射成 TOKEN_INVALID（不泄漏 OkHttp 异常类型）
        val badToken = runCatching { api.status(endpoint, "not-a-real-token") }.exceptionOrNull()
        assertNotNull("坏令牌必须抛 SyncApiException", badToken)
        assertTrue("异常类型应为 SyncApiException，实际 $badToken", badToken is SyncApiException)
        assertEquals(SyncErrorCode.TOKEN_INVALID, (badToken as SyncApiException).code)
    }

    /**
     * 服务地址来源（任一）：环境变量 → JVM 系统属性 → `app/build/e2e-base-url.txt`。
     *
     * 为什么给第三个来源：Gradle 测试 worker 的环境变量继承自 daemon 进程，daemon 复用会让
     * 「本次设了变量却还是跳过」变成随机现象；写文件最可控（且 `build/` 不进版本库）。
     */
    private fun baseUrlFrom(): String? {
        System.getenv(ENV_KEY)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getProperty(PROP_KEY)?.takeIf { it.isNotBlank() }?.let { return it }
        return listOf(
            File("build/e2e-base-url.txt"),
            File("app/build/e2e-base-url.txt"),
        ).firstOrNull { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val ENV_KEY = "MINIBOOK_E2E_BASE_URL"
        const val PROP_KEY = "minibook.e2e.baseUrl"
        const val DEVICE = "jvm-e2e-device-0001"
    }
}
