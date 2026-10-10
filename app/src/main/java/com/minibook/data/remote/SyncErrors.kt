package com.minibook.data.remote

/**
 * 同步错误码（服务端 `docs/14_cloud_sync.md §3.5` + 客户端侧网络/配置错误）。
 *
 * 服务端错误码与 `server/app/errors.py` 的 `ERROR_STATUS` 一一对应；后半段是**客户端自产**码，
 * 服务端永不会返回（`wire == null`），用于把网络/配置问题与服务端业务错误区分开。
 */
enum class SyncErrorCode(val wire: String?) {
    // —— 服务端业务码 ——
    INVALID_CREDENTIALS("invalid_credentials"),
    USERNAME_TAKEN("username_taken"),
    VALIDATION_ERROR("validation_error"),
    TOKEN_EXPIRED("token_expired"),
    TOKEN_INVALID("token_invalid"),
    TOKEN_REVOKED("token_revoked"),
    NOT_FOUND("not_found"),
    RATE_LIMITED("rate_limited"),
    SERVER_ERROR("server_error"),

    // —— 客户端自产码 ——
    /** 网络不可用 / 服务器无响应 / 超时。 */
    NETWORK_ERROR(null),

    /** 系统拦截明文 HTTP（正式包连 `http://` 内网地址）——单独成一码，便于给出可操作提示。 */
    CLEARTEXT_BLOCKED(null),

    /** 服务器地址非法或正式包填了 http。 */
    INVALID_BASE_URL(null),

    /** 本地尚未登录。 */
    NOT_LOGGED_IN(null),

    /** 无法识别的错误码（服务端新增了本客户端不认识的码）。 */
    UNKNOWN(null),
    ;

    companion object {
        /** 服务端错误码 → 枚举；未知码归 [UNKNOWN]（不抛异常，保证前向兼容）。 */
        fun fromWire(wire: String?): SyncErrorCode =
            entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/**
 * 同步调用失败。[code] 为分类依据，[message] 用于日志/诊断，
 * 面向用户的文案一律走 [userMessage]（只含计数与原因，不含账目明细，DEC-034）。
 */
class SyncApiException(
    val code: SyncErrorCode,
    override val message: String,
    val httpStatus: Int = 0,
    /** 整批校验失败时出错条目的下标（服务端返回）。 */
    val index: Int? = null,
    val entityId: String? = null,
) : Exception(message) {

    /** 是否值得原样重试（网络/服务端临时故障；业务错误重试无意义）。 */
    val isRetryable: Boolean
        get() = code == SyncErrorCode.NETWORK_ERROR ||
            code == SyncErrorCode.SERVER_ERROR ||
            code == SyncErrorCode.RATE_LIMITED

    /** 面向用户的中文文案（**不出现任何账目明细**）。 */
    fun userMessage(): String = when (code) {
        SyncErrorCode.INVALID_CREDENTIALS -> "用户名或口令不正确"
        SyncErrorCode.USERNAME_TAKEN -> "该用户名已被占用，请换一个"
        SyncErrorCode.VALIDATION_ERROR ->
            if (index != null) "数据校验失败（第 ${index + 1} 条），未写入云端，请升级 App 后重试" else "数据校验失败，未写入云端"
        SyncErrorCode.TOKEN_EXPIRED -> "登录已过期，请重新登录"
        SyncErrorCode.TOKEN_INVALID -> "登录状态无效，请重新登录"
        SyncErrorCode.TOKEN_REVOKED -> "登录已在其他设备失效，请重新登录"
        SyncErrorCode.NOT_FOUND -> "云端未找到该数据"
        SyncErrorCode.RATE_LIMITED -> "操作过于频繁，请稍后再试"
        SyncErrorCode.SERVER_ERROR -> "服务器暂时不可用，请稍后重试"
        SyncErrorCode.NETWORK_ERROR -> "网络不可用或连不上服务器（请检查地址与同一 Wi-Fi）"
        SyncErrorCode.CLEARTEXT_BLOCKED -> "系统拦截了明文 HTTP：正式版仅支持 https://，联调请用调试版"
        SyncErrorCode.INVALID_BASE_URL -> "服务器地址无效（示例：http://192.168.1.5:8000）"
        SyncErrorCode.NOT_LOGGED_IN -> "请先登录云账号"
        SyncErrorCode.UNKNOWN -> "同步失败（${message.ifBlank { "未知错误" }}）"
    }
}
