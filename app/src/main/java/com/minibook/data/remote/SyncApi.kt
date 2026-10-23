package com.minibook.data.remote

import com.minibook.di.IoDispatcher
import java.io.IOException
import java.net.UnknownServiceException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 云同步 HTTP 客户端（F-15）：手写 OkHttp + kotlinx-serialization，不引 Retrofit。
 *
 * 契约：所有方法 suspend，且**不抛 OkHttp/IO 异常** —— 统一转成 [SyncApiException]，
 * 让上层只需处理一种异常类型（错误码见 [SyncErrorCode]）。
 */
class SyncApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SyncRemote {

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        const val PATH_REGISTER = "/api/v1/auth/register"
        const val PATH_LOGIN = "/api/v1/auth/login"
        const val PATH_REFRESH = "/api/v1/auth/refresh"
        const val PATH_LOGOUT = "/api/v1/auth/logout"
        const val PATH_ME = "/api/v1/me"
        const val PATH_DELETE_ACCOUNT = "/api/v1/auth/delete-account"
        const val PATH_PUSH = "/api/v1/sync/push"
        const val PATH_PULL = "/api/v1/sync/pull"
        const val PATH_STATUS = "/api/v1/sync/status"
    }

    // ==== 认证 ====

    override suspend fun register(baseUrl: String, request: SyncRegisterRequest): SyncUserDto =
        call(
            baseUrl = baseUrl,
            path = PATH_REGISTER,
            method = "POST",
            body = json.encodeToString(SyncRegisterRequest.serializer(), request),
            accessToken = null,
            deserializer = SyncUserDto.serializer(),
        )

    override suspend fun login(baseUrl: String, request: SyncLoginRequest): SyncTokenPairDto =
        call(
            baseUrl = baseUrl,
            path = PATH_LOGIN,
            method = "POST",
            body = json.encodeToString(SyncLoginRequest.serializer(), request),
            accessToken = null,
            deserializer = SyncTokenPairDto.serializer(),
        )

    override suspend fun refresh(baseUrl: String, request: SyncRefreshRequest): SyncTokenPairDto =
        call(
            baseUrl = baseUrl,
            path = PATH_REFRESH,
            method = "POST",
            body = json.encodeToString(SyncRefreshRequest.serializer(), request),
            accessToken = null,
            deserializer = SyncTokenPairDto.serializer(),
        )

    override suspend fun logout(baseUrl: String, request: SyncLogoutRequest) {
        callRaw(
            baseUrl = baseUrl,
            path = PATH_LOGOUT,
            method = "POST",
            body = json.encodeToString(SyncLogoutRequest.serializer(), request),
            accessToken = null,
        )
    }

    override suspend fun me(baseUrl: String, accessToken: String): SyncUserDto =
        call(
            baseUrl = baseUrl,
            path = PATH_ME,
            method = "GET",
            body = null,
            accessToken = accessToken,
            deserializer = SyncUserDto.serializer(),
        )

    override suspend fun updateProfile(
        baseUrl: String,
        accessToken: String,
        request: SyncProfileUpdateRequest,
    ): SyncProfileUpdateResponse =
        call(
            baseUrl = baseUrl,
            path = PATH_ME,
            method = "PATCH",
            body = json.encodeToString(SyncProfileUpdateRequest.serializer(), request),
            accessToken = accessToken,
            deserializer = SyncProfileUpdateResponse.serializer(),
        )

    override suspend fun deleteAccount(
        baseUrl: String,
        accessToken: String,
        request: SyncDeleteAccountRequest,
    ) {
        // 用 POST 而不是 DELETE：DELETE 带请求体在部分反向代理/网关下会被丢掉（账户删除必须可靠）
        callRaw(
            baseUrl = baseUrl,
            path = PATH_DELETE_ACCOUNT,
            method = "POST",
            body = json.encodeToString(SyncDeleteAccountRequest.serializer(), request),
            accessToken = accessToken,
        )
    }

    // ==== 同步 ====

    override suspend fun push(
        baseUrl: String,
        accessToken: String,
        request: SyncPushRequest,
    ): SyncPushResponseDto =
        call(
            baseUrl = baseUrl,
            path = PATH_PUSH,
            method = "POST",
            body = json.encodeToString(SyncPushRequest.serializer(), request),
            accessToken = accessToken,
            deserializer = SyncPushResponseDto.serializer(),
        )

    override suspend fun pull(
        baseUrl: String,
        accessToken: String,
        sinceSeq: Long,
        limit: Int,
    ): SyncPullResponseDto =
        call(
            baseUrl = baseUrl,
            path = "$PATH_PULL?since_seq=$sinceSeq&limit=$limit",
            method = "GET",
            body = null,
            accessToken = accessToken,
            deserializer = SyncPullResponseDto.serializer(),
        )

    override suspend fun status(baseUrl: String, accessToken: String): SyncStatusDto =
        call(
            baseUrl = baseUrl,
            path = PATH_STATUS,
            method = "GET",
            body = null,
            accessToken = accessToken,
            deserializer = SyncStatusDto.serializer(),
        )

    // ==== 内部 ====

    private suspend fun <T> call(
        baseUrl: String,
        path: String,
        method: String,
        body: String?,
        accessToken: String?,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val text = callRaw(baseUrl, path, method, body, accessToken)
        return try {
            json.decodeFromString(deserializer, text)
        } catch (e: Exception) {
            throw SyncApiException(SyncErrorCode.SERVER_ERROR, "响应无法解析：${e.message}")
        }
    }

    private suspend fun callRaw(
        baseUrl: String,
        path: String,
        method: String,
        body: String?,
        accessToken: String?,
    ): String {
        val requestBuilder = Request.Builder().url(SyncUrlValidator.endpoint(baseUrl, path))
        if (accessToken != null) requestBuilder.header("Authorization", "Bearer $accessToken")
        when (method) {
            "GET" -> requestBuilder.get()
            "PATCH" -> requestBuilder.patch((body ?: "").toRequestBody(JSON_CONTENT_TYPE.toMediaType()))
            else -> requestBuilder.post((body ?: "").toRequestBody(JSON_CONTENT_TYPE.toMediaType()))
        }

        // 读 body 同样是网络 I/O：**必须**留在 withContext 里面。
        // 2026-10-12 实测缺陷：原实现把 `resp.body?.string()` 放在 withContext 外面，
        // 小响应侥幸已进缓冲区、大响应就会在调用方线程真读 socket ⇒ NetworkOnMainThreadException
        // （自动同步没暴露它，因为 SyncUseCase 整段已在 IO 上；账号页直接调本方法才踩到）。
        val (response, text) = try {
            withContext(ioDispatcher) {
                client.newCall(requestBuilder.build()).execute().use { resp ->
                    resp to resp.body?.string().orEmpty()
                }
            }
        } catch (e: UnknownServiceException) {
            // OkHttp 对「明文被系统拦截」抛此异常（正式包连 http:// 内网地址时）
            throw SyncApiException(SyncErrorCode.CLEARTEXT_BLOCKED, e.message.orEmpty())
        } catch (e: IOException) {
            throw SyncApiException(SyncErrorCode.NETWORK_ERROR, e.message.orEmpty())
        }

        if (!response.isSuccessful) throw toApiException(response.code, text)
        return text
    }

    /** HTTP 非 2xx → [SyncApiException]；优先用服务端错误体，解析不了就按状态码归类。 */
    private fun toApiException(httpStatus: Int, text: String): SyncApiException {
        val envelope = try {
            json.decodeFromString(SyncErrorEnvelopeDto.serializer(), text)
        } catch (e: Exception) {
            null
        }
        if (envelope != null) {
            return SyncApiException(
                code = SyncErrorCode.fromWire(envelope.error.code),
                message = envelope.error.message.ifBlank { "HTTP $httpStatus" },
                httpStatus = httpStatus,
                index = envelope.error.index,
                entityId = envelope.error.entityId,
            )
        }
        val fallbackCode = when {
            httpStatus == 401 -> SyncErrorCode.TOKEN_INVALID
            httpStatus == 404 -> SyncErrorCode.NOT_FOUND
            httpStatus == 429 -> SyncErrorCode.RATE_LIMITED
            httpStatus >= 500 -> SyncErrorCode.SERVER_ERROR
            else -> SyncErrorCode.UNKNOWN
        }
        return SyncApiException(fallbackCode, "HTTP $httpStatus", httpStatus = httpStatus)
    }
}
