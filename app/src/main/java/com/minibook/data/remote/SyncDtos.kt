package com.minibook.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 云同步 HTTP 契约（F-15），与 `server/app/schemas.py` **一一对应**。
 *
 * 命名口径（`docs/14_cloud_sync.md §3.5`）：**信封字段 snake_case**（`entity_type` / `entity_id` /
 * `ledger_id` / `updated_at`），`payload` 内是 Android Room 实体的**原样 camelCase 列**。
 * 故本文件里只有信封字段带 `@SerialName`，payload 用 [JsonObject] 原样透传（少一层转换 = 少一类错位）。
 */
@Serializable
data class SyncRegisterRequest(
    val username: String,
    val password: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class SyncLoginRequest(
    val username: String,
    val password: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class SyncRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class SyncLogoutRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class SyncUserDto(
    @SerialName("user_id") val userId: String,
    val username: String,
    /** 显示名（可改；null ⇒ UI 回退显示 [username]）。2026-10-12 随「账号」页加入。 */
    val nickname: String? = null,
    /** 邮箱 / 个性签名 / 地区（均可空 = 未填写）。2026-10-12 第二轮随「资料」扩充加入。 */
    val email: String? = null,
    val bio: String? = null,
    val region: String? = null,
    /** 注册时间（**带 Z 的 UTC ISO 串**；展示前必须转本地时区，见 [com.minibook.ui.settings.SyncPresenter.humanTime]）。 */
    @SerialName("created_at") val createdAt: String? = null,
    /** 最近一次登录时间（同上；仅 `GET /me` 返回）。 */
    @SerialName("last_login_at") val lastLoginAt: String? = null,
)

/**
 * 改资料（2026-10-12）。**哪个字段为 null 就是「不改哪项」**：
 * 客户端 `Json` 配置 `explicitNulls = false` ⇒ null 字段不上线，服务端按「字段是否出现」判定，
 * 所以「只改昵称」不会被误判成「顺带清空口令」。
 *
 * 清空显示名传**空串**（不是 null）：空串会被服务端归一为「清除昵称」。
 */
@Serializable
data class SyncProfileUpdateRequest(
    val nickname: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val region: String? = null,
    @SerialName("old_password") val oldPassword: String? = null,
    @SerialName("new_password") val newPassword: String? = null,
)

@Serializable
data class SyncProfileUpdateResponse(
    val user: SyncUserDto,
    /** 仅当本次改了口令时非空：旧刷新令牌已全部撤销，必须用它覆盖本地会话。 */
    val tokens: SyncTokenPairDto? = null,
)

/** 注销账号（不可撤销）：口令二次确认。 */
@Serializable
data class SyncDeleteAccountRequest(val password: String)

@Serializable
data class SyncTokenPairDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long,
    val user: SyncUserDto,
)

/** 一条待同步记录。`deleted=true` 为墓碑；墓碑可省 `payload`。 */
@Serializable
data class SyncItemDto(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("ledger_id") val ledgerId: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val payload: JsonObject? = null,
)

@Serializable
data class SyncPushRequest(
    @SerialName("device_id") val deviceId: String? = null,
    val items: List<SyncItemDto>,
)

/** 服务端记录（pull 下发 / push 冲突回传）。 */
@Serializable
data class SyncServerItemDto(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("ledger_id") val ledgerId: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
    val payload: JsonObject? = null,
    @SerialName("server_seq") val serverSeq: Long,
)

@Serializable
data class SyncConflictDto(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    /** 服务端判定的冲突原因，目前只有 `server_newer`。 */
    val reason: String,
    val server: SyncServerItemDto,
)

@Serializable
data class SyncPushResponseDto(
    val applied: Int,
    val skipped: Int,
    val conflicts: List<SyncConflictDto> = emptyList(),
    @SerialName("server_seq") val serverSeq: Long,
)

@Serializable
data class SyncPullResponseDto(
    val items: List<SyncServerItemDto> = emptyList(),
    @SerialName("next_seq") val nextSeq: Long,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class SyncStatusDto(
    /** 本账号自己的最高 server_seq（拉完后应达到的游标）。 */
    @SerialName("server_seq") val serverSeq: Long,
    /** 服务端全局计数器（跨账号共享，仅诊断用）。 */
    @SerialName("server_seq_global") val serverSeqGlobal: Long = 0,
    val counts: Map<String, Int> = emptyMap(),
    @SerialName("server_time") val serverTime: String = "",
) {
    /** 云端记录总数（UI 状态行「云端记录数」）。 */
    val totalCount: Int get() = counts.values.sum()
}

@Serializable
data class SyncErrorBodyDto(
    val code: String,
    val message: String = "",
    /** 整批校验失败时，出错条目在 `items` 中的下标。 */
    val index: Int? = null,
    @SerialName("entity_id") val entityId: String? = null,
)

@Serializable
data class SyncErrorEnvelopeDto(
    val error: SyncErrorBodyDto,
)
