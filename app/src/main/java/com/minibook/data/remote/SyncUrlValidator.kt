package com.minibook.data.remote

/**
 * 服务器地址校验与规范化（纯函数，可 JVM 单测）。
 *
 * 规则（`docs/14_cloud_sync.md §6`）：
 * - 允许 `http://` 与 `https://`；
 * - **未写 scheme 时**：调试包补 `http://`（内网联调最常用），正式包补 `https://`；
 * - **正式包禁止明文 http**（平台默认也会拦，但这里提前给出可读提示，而不是等网络层报错）；
 * - 去掉尾部 `/`，允许带路径前缀（如反向代理下的 `/minibook`）。
 */
object SyncUrlValidator {

    private const val DEFAULT_DEBUG_SCHEME = "http://"
    private const val DEFAULT_RELEASE_SCHEME = "https://"

    /** 主机名/IP + 可选端口。主机名允许字母数字与 `.-_`，IPv6 用方括号。 */
    private val HOST_PATTERN = Regex("^([A-Za-z0-9._-]+|\\[[0-9A-Fa-f:]+])(:(\\d{1,5}))?$")

    /**
     * @param raw 用户输入
     * @param debuggable 是否调试包（`ApplicationInfo.FLAG_DEBUGGABLE`，正式包禁明文）
     * @return 规范化后的 base url（无尾斜杠）
     * @throws SyncApiException [SyncErrorCode.INVALID_BASE_URL]
     */
    fun normalize(raw: String, debuggable: Boolean): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw invalid("请填写服务器地址")

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> (if (debuggable) DEFAULT_DEBUG_SCHEME else DEFAULT_RELEASE_SCHEME) + trimmed
        }

        val scheme = withScheme.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") throw invalid("只支持 http:// 或 https://")
        if (scheme == "http" && !debuggable) {
            throw SyncApiException(
                SyncErrorCode.INVALID_BASE_URL,
                "正式版禁止明文 HTTP：$withScheme",
            )
        }

        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
        if (host.isBlank()) throw invalid("缺少主机名或 IP")
        val match = HOST_PATTERN.matchEntire(host) ?: throw invalid("主机名或端口不合法：$host")
        val port = match.groupValues[3]
        if (port.isNotEmpty() && (port.toIntOrNull() ?: 0) !in 1..65535) {
            throw invalid("端口超出范围：$port")
        }

        return withScheme.trimEnd('/')
    }

    /** 拼接接口路径（base 已无尾斜杠，path 以 `/` 开头）。 */
    fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + path

    private fun invalid(detail: String) = SyncApiException(
        SyncErrorCode.INVALID_BASE_URL,
        "服务器地址无效：$detail",
    )
}
