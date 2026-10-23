package com.minibook.data.remote

import com.minibook.BuildConfig

/**
 * 服务器地址校验与规范化（纯函数，可 JVM 单测）。
 *
 * 规则（`docs/14_cloud_sync.md §6`；2026-10-23 修订）：
 * - 允许 `http://` 与 `https://`；
 * - **未写 scheme 时**：调试包补 `http://`（内网联调最常用），正式包补 `https://`；
 * - **明文白名单**：调试包全放行；正式包**只放行内置入口那个 host**（线上暂不加证书，
 *   走明文 + 无关端口，DEC-053），其余地址必须 `https://`——平台层的
 *   `network_security_config` 是同口径兜底，这里提前给出可读提示而不是等网络层报错；
 * - 去掉尾部 `/`，允许带路径前缀（如反向代理下的 `/minibook`）。
 */
object SyncUrlValidator {

    private const val DEFAULT_DEBUG_SCHEME = "http://"
    private const val DEFAULT_RELEASE_SCHEME = "https://"

    /** 主机名/IP + 可选端口。主机名允许字母数字与 `.-_`，IPv6 用方括号。 */
    private val HOST_PATTERN = Regex("^([A-Za-z0-9._-]+|\\[[0-9A-Fa-f:]+])(:(\\d{1,5}))?$")

    /**
     * @param raw 用户输入或已保存地址
     * @param debuggable 是否调试包（`ApplicationInfo.FLAG_DEBUGGABLE`）
     * @param builtIn 该构建类型的内置地址（`BuildConfig.SYNC_BASE_URL`），
     *   用于判定正式包放行的唯一明文 host
     * @return 规范化后的 base url（无尾斜杠）
     * @throws SyncApiException [SyncErrorCode.INVALID_BASE_URL]
     */
    fun normalize(
        raw: String,
        debuggable: Boolean,
        builtIn: String = BuildConfig.SYNC_BASE_URL,
    ): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw invalid("请填写服务器地址")

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> (if (debuggable) DEFAULT_DEBUG_SCHEME else DEFAULT_RELEASE_SCHEME) + trimmed
        }

        val scheme = withScheme.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") throw invalid("只支持 http:// 或 https://")

        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
        if (host.isBlank()) throw invalid("缺少主机名或 IP")
        val match = HOST_PATTERN.matchEntire(host) ?: throw invalid("主机名或端口不合法：$host")
        val hostName = match.groupValues[1] // 不含端口
        val port = match.groupValues[3]
        if (port.isNotEmpty() && (port.toIntOrNull() ?: 0) !in 1..65535) {
            throw invalid("端口超出范围：$port")
        }

        // 明文放行按 **host** 比对（平台侧 domain-config 也是按 host，不区分端口）
        if (scheme == "http" && !debuggable && !hostName.equals(cleartextHostOf(builtIn), ignoreCase = true)) {
            throw SyncApiException(
                SyncErrorCode.INVALID_BASE_URL,
                "正式版只允许 https://（仅放行内置入口 ${cleartextHostOf(builtIn) ?: "—"}）：$withScheme",
            )
        }

        return withScheme.trimEnd('/')
    }

    /** 拼接接口路径（base 已无尾斜杠，path 以 `/` 开头）。 */
    fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + path

    /** 取地址里的 host（不含端口与路径）；非明文或取不出时返回 null。 */
    private fun cleartextHostOf(url: String): String? {
        val hostPort = url.takeIf { it.startsWith("http://", ignoreCase = true) }
            ?.substringAfter("://")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val hostName = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']') + "]" // IPv6 字面量
        } else {
            hostPort.substringBefore(':')
        }
        return hostName.lowercase()
    }

    private fun invalid(detail: String) = SyncApiException(
        SyncErrorCode.INVALID_BASE_URL,
        "服务器地址无效：$detail",
    )
}
