package com.minibook.data.remote

/**
 * 云同步远端调用契约（[SyncApi] 的接口形态）。
 *
 * 存在的唯一理由：让上层（[SyncTokenAuthenticator]、同步 UseCase）在 JVM 单测里
 * 可以用假实现替换真实 HTTP，从而验证「401 → 刷新一次 → 重试」这类编排逻辑。
 */
interface SyncRemote {

    suspend fun register(baseUrl: String, request: SyncRegisterRequest): SyncUserDto

    suspend fun login(baseUrl: String, request: SyncLoginRequest): SyncTokenPairDto

    suspend fun refresh(baseUrl: String, request: SyncRefreshRequest): SyncTokenPairDto

    suspend fun logout(baseUrl: String, request: SyncLogoutRequest)

    suspend fun me(baseUrl: String, accessToken: String): SyncUserDto

    /** 改资料 / 改口令（2026-10-12）。改口令时服务端返回新的令牌对，须覆盖本地会话。 */
    suspend fun updateProfile(
        baseUrl: String,
        accessToken: String,
        request: SyncProfileUpdateRequest,
    ): SyncProfileUpdateResponse

    /** 注销账号（不可撤销，删云端全部副本）。 */
    suspend fun deleteAccount(
        baseUrl: String,
        accessToken: String,
        request: SyncDeleteAccountRequest,
    )

    suspend fun push(baseUrl: String, accessToken: String, request: SyncPushRequest): SyncPushResponseDto

    suspend fun pull(
        baseUrl: String,
        accessToken: String,
        sinceSeq: Long,
        limit: Int,
    ): SyncPullResponseDto

    suspend fun status(baseUrl: String, accessToken: String): SyncStatusDto
}
