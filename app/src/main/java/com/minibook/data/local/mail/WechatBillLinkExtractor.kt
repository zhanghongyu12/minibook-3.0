package com.minibook.data.local.mail

import javax.inject.Inject

/**
 * 微信账单邮件正文下载链接提取器（F-13，纯 Kotlin，JVM 可单测）。
 *
 * **刻意手写字符扫描、零正则**：Android 的 `java.util.regex` 底层是 ICU，与 JDK 语义
 * 存在差异（历史上 `\}` 曾在真机抛 `PatternSyntaxException`；旧 URL 候选正则在
 * JVM 命中、真机却 NoLink）。本提取器处于 F-13 微信账单取链关键路径，彻底不依赖正则，
 * 语义与旧正则实现保持一致。
 *
 * 微信账单邮件为 `multipart/ALTERNATIVE`，下载链接只存在于 HTML 部分的
 * `<a href="https://tenpay.wechatpay.cn/userroll/...">点击下载</a>` 里（纯文本
 * 部分只有「点击下载」四个字、无 URL）。本提取器在 [ImapMailClient] 输出的正文
 * 文本（HTML 部分已去标签但**保留了 href 里的 URL**）上按「域名 + 路径」精确
 * 匹配账单下载链接——正文里同时存在的图片/帮助页等干扰链接
 * （如 `http://imgcache.qq.com/...`、`https://wx.gtimg.com/...png`）不会被误选。
 */
class WechatBillLinkExtractor @Inject constructor() {

    /** 从正文文本中提取微信账单 zip 下载链接；找不到返回 null。 */
    fun extract(bodyText: String): String? {
        if (bodyText.isBlank()) return null
        var i = 0
        while (i < bodyText.length) {
            val start = indexOfScheme(bodyText, i)
            if (start < 0) return null
            val end = scanUrlEnd(bodyText, start)
            if (end > start) {
                val candidate = bodyText.substring(start, end)
                if (isBillDownloadUrl(candidate)) {
                    // HTML 实体反转义：去标签前 href 里的 `&amp;` 须还原为 `&`。
                    return candidate.replace("&amp;", "&")
                }
                i = end
            } else {
                // `http://` 后紧跟分隔符（空候选）：跳过该 scheme 继续扫，避免死循环。
                i = start + 1
            }
        }
        return null
    }

    // ------------------------------------------------------------------ 候选扫描

    /** 从 [from] 起找最早出现的 `http://` / `https://`（大小写不敏感）；找不到返回 -1。 */
    private fun indexOfScheme(text: String, from: Int): Int {
        for (i in from..text.length - HTTP_PREFIX.length) {
            if (text.regionMatches(i, HTTP_PREFIX, 0, HTTP_PREFIX.length, ignoreCase = true) ||
                text.regionMatches(i, HTTPS_PREFIX, 0, HTTPS_PREFIX.length, ignoreCase = true)
            ) {
                return i
            }
        }
        return -1
    }

    /** 该字符是否可作为 URL 的一部分（ASCII 可见字符且非分隔符）。 */
    private fun isUrlChar(c: Char): Boolean {
        // 空白、控制字符（<0x20）、DEL（0x7F）与非 ASCII（>=0x7F，含中文/emoji）统一截断。
        if (c.code < 0x21 || c.code > 0x7e) return false
        return URL_DELIMITERS.indexOf(c) < 0
    }

    /** 从 scheme 起点向后吃 URL 字符直到首个分隔符；返回候选终点（不含）。 */
    private fun scanUrlEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && isUrlChar(text[i])) i++
        return i
    }

    // ------------------------------------------------------------------ 命中判定

    /**
     * 严格判定候选是否微信账单下载链接：host **精确等于** `tenpay.wechatpay.cn`
     * （允许可选 `:端口`，端口为纯数字且后必须紧跟 `/`，防仿冒域名
     * `tenpay.wechatpay.cn.evil.com`），其后路径以
     * `/userroll/userbilldownload/downloadfilefromemail` 开头。
     * 不用「取第一个 http 链接」——正文里的图片/帮助页链接会先出现。
     */
    private fun isBillDownloadUrl(candidate: String): Boolean {
        // 候选必以 http:// 或 https:// 开头（由 indexOfScheme 保证）。
        val hostStart = if (
            candidate.length >= HTTPS_PREFIX.length &&
            candidate.regionMatches(0, HTTPS_PREFIX, 0, HTTPS_PREFIX.length, ignoreCase = true)
        ) {
            HTTPS_PREFIX.length
        } else {
            HTTP_PREFIX.length
        }
        // host 终点：首个 `/`（路径）或 `:`（端口）；二者皆非则无路径，不命中。
        var i = hostStart
        while (i < candidate.length && candidate[i] != '/' && candidate[i] != ':') i++
        val host = candidate.substring(hostStart, i)
        if (!host.equals(BILL_HOST, ignoreCase = true)) return false
        val pathStart: Int
        if (i < candidate.length && candidate[i] == ':') {
            // 可选端口：至少一位数字，其后必须紧跟 `/`（`tenpay.wechatpay.cn:443userroll` 不算）。
            var j = i + 1
            while (j < candidate.length && candidate[j] in '0'..'9') j++
            if (j == i + 1) return false
            if (j >= candidate.length || candidate[j] != '/') return false
            pathStart = j
        } else if (i < candidate.length && candidate[i] == '/') {
            pathStart = i
        } else {
            return false
        }
        return candidate.regionMatches(pathStart, BILL_PATH, 0, BILL_PATH.length, ignoreCase = true)
    }

    private companion object {
        /** `http://` 与 `https://` 前缀（大小写不敏感扫描）。 */
        const val HTTP_PREFIX = "http://"
        const val HTTPS_PREFIX = "https://"

        /**
         * URL 分隔符集合（与旧负字符类口径严格等价的可见 ASCII 部分）：`"`、`'`、
         * `<`、`>`、`\`、反引号、`{`、`}`、`|`、`^`——其余旧负字符类排除项
         * （空白、控制字符、DEL、非 ASCII）由 [isUrlChar] 的码点范围判断统一截断。
         * 即候选 URL 在引号、尖括号、空白、反斜杠（JSON/HTML 残留）或中文处正确截断，
         * 不吞终止符。
         */
        const val URL_DELIMITERS = "\"'<>\\`{}|^"

        /** 账单下载 host（大小写不敏感严格相等，防仿冒域名）。 */
        const val BILL_HOST = "tenpay.wechatpay.cn"

        /** 账单下载路径前缀（host 或端口后必须紧跟本串；大小写不敏感）。 */
        const val BILL_PATH = "/userroll/userbilldownload/downloadfilefromemail"
    }
}
