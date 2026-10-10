package com.minibook.data.remote

import javax.inject.Inject

/**
 * 令牌编排：**401 → 刷新一次 → 重试**；刷新失败清账号态（`docs/14_cloud_sync.md §5.2`）。
 *
 * 与设计原文的差分（已记入 `14_cloud_sync.md §12`）：设计写的是 OkHttp `Authenticator`
 * （同步回调、需要阻塞式刷新），实现改为 **suspend 包装器** —— 与协程调用链一致，
 * 且可在 JVM 单测里用假 [SyncRemote] 验证「只重试一次」「网络故障不清账号」等口径。
 *
 * 刷新失败的分流（关键，避免误清账号）：
 * - [SyncErrorCode.NETWORK_ERROR] / [SyncErrorCode.SERVER_ERROR]：**保留**账号态（下次重试即可）；
 * - 令牌类错误（`token_revoked` / `token_invalid` / `token_expired`）：**清**账号态，提示重新登录。
 */
class SyncTokenAuthenticator @Inject constructor(
    private val store: SyncAuthStore,
    private val refreshCall: suspend (baseUrl: String, request: SyncRefreshRequest) -> SyncTokenPairDto,
) {

    /** 一次已授权调用的上下文。 */
    data class Session(val baseUrl: String, val accessToken: String)

    /**
     * 用当前访问令牌执行 [block]；若服务端判令牌过期/无效，则刷新一次后重试 [block]。
     *
     * @throws SyncApiException 未登录（[SyncErrorCode.NOT_LOGGED_IN]）或刷新最终失败
     */
    suspend fun <T> withToken(block: suspend (Session) -> T): T {
        val state = store.current()
        if (!state.isLoggedIn) {
            throw SyncApiException(SyncErrorCode.NOT_LOGGED_IN, "尚未登录云账号")
        }
        val stored = store.accessToken()
            ?: throw SyncApiException(SyncErrorCode.NOT_LOGGED_IN, "本地令牌不可用，请重新登录")
        val access = if (store.isAccessExpiring()) refreshOrThrow(state.baseUrl) else stored

        return try {
            block(Session(state.baseUrl, access))
        } catch (expired: SyncApiException) {
            if (expired.code != SyncErrorCode.TOKEN_EXPIRED && expired.code != SyncErrorCode.TOKEN_INVALID) {
                throw expired
            }
            block(Session(state.baseUrl, refreshOrThrow(state.baseUrl)))
        }
    }

    /** 刷新令牌并落盘新令牌；失败时按上述分流处理账号态。 */
    suspend fun refreshOrThrow(baseUrl: String): String {
        val refreshToken = store.refreshToken()
        if (refreshToken.isNullOrEmpty()) {
            store.clearSession()
            throw SyncApiException(SyncErrorCode.TOKEN_INVALID, "本地无刷新令牌")
        }
        val pair = try {
            refreshCall(baseUrl, SyncRefreshRequest(refreshToken = refreshToken, deviceId = store.deviceId()))
        } catch (e: SyncApiException) {
            if (e.code == SyncErrorCode.NETWORK_ERROR || e.code == SyncErrorCode.SERVER_ERROR) {
                throw e // 可重试：保留账号态
            }
            store.clearSession()
            throw e
        }
        store.updateTokens(pair.accessToken, pair.refreshToken, pair.expiresIn)
        return pair.accessToken
    }
}
