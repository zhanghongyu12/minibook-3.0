package com.minibook.data.local.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载失败（含链接过期）。message 只描述故障类别，**不含 URL 全文**——
 * 微信下载链接的 query 带签名 token，视同凭证，不得进异常信息与日志。
 */
class BillDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 一次成功下载的结果：文件名（可能为 null，由 Content-Disposition 解析）+ 全量字节。 */
data class DownloadedFile(val fileName: String?, val bytes: ByteArray)

/**
 * 微信账单 zip 下载器（F-13）。
 *
 * 微信账单邮件正文里的下载链接（[WechatBillLinkExtractor] 提取）指向
 * `tenpay.wechatpay.cn`，返回 zip 附件。实现约束：
 *
 * 1. **只允许 https**（初始 URL 与每一跳重定向都校验，非 https 直接抛异常）；
 * 2. 手动跟随重定向（`setInstanceFollowRedirects(false)` + 循环，**最多 5 跳**），
 *    以便逐跳校验协议；用 `HttpURLConnection`，不引入新依赖；
 * 3. 连接/读超时各 30s；带浏览器 User-Agent（部分 CDN 拒绝无 UA 请求）；
 * 4. 非 200（微信链接有效期 7 天，过期返回 403/404/410）统一按「链接失效」报错；
 * 5. 异常 message 不带 URL（含签名 token）。
 */
@Singleton
class MailBillDownloader @Inject constructor() {

    /** HTTPS GET 下载；跟随重定向（≤5 跳）；非 200 抛 [BillDownloadException]。 */
    suspend fun download(url: String): DownloadedFile = withContext(Dispatchers.IO) {
        try {
            downloadBlocking(url)
        } catch (e: BillDownloadException) {
            throw e
        } catch (e: SocketTimeoutException) {
            // SocketTimeoutException 是 IOException 子类，必须先于 IOException 捕获。
            throw BillDownloadException(MESSAGE_TIMEOUT, e)
        } catch (e: Exception) {
            throw BillDownloadException(MESSAGE_FAILURE, e)
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private fun downloadBlocking(initialUrl: String): DownloadedFile {
        var current = requireHttps(initialUrl)
        var redirects = 0
        while (true) {
            val connection = openConnection(current)
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                connection.disconnect()
                throw e
            }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw BillDownloadException(MESSAGE_FAILURE)
                if (++redirects > MAX_REDIRECTS) throw BillDownloadException(MESSAGE_TOO_MANY_REDIRECTS)
                // 相对 Location 也要解析成绝对地址，且每一跳仍强制 https。
                current = requireHttps(resolve(current, location))
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                // 403/404/410 等：微信链接过期/失效的典型表现（有效期 7 天）。
                throw BillDownloadException(MESSAGE_LINK_EXPIRED)
            }
            try {
                val fileName = ContentDispositionParser.parse(connection.getHeaderField("Content-Disposition"))
                val bytes = connection.inputStream.use { it.readBytes() }
                return DownloadedFile(fileName, bytes)
            } finally {
                connection.disconnect()
            }
        }
    }

    /** 打开连接：30s 双超时 + 关闭自动重定向（手动逐跳校验 https）+ 浏览器 UA。 */
    private fun openConnection(url: String): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    /** 只允许 https（大小写不敏感）；非 https 或无法解析的 URL 直接失败。 */
    private fun requireHttps(url: String): String {
        val scheme = try {
            URI(url).scheme
        } catch (e: Exception) {
            null
        }
        if (!scheme.equals("https", ignoreCase = true)) {
            throw BillDownloadException(MESSAGE_HTTPS_ONLY)
        }
        return url
    }

    /** 相对重定向地址按当前 URL 解析为绝对地址。 */
    private fun resolve(base: String, location: String): String = try {
        URI(base).resolve(location.trim()).toString()
    } catch (e: Exception) {
        throw BillDownloadException(MESSAGE_FAILURE, e)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5
        const val MESSAGE_TIMEOUT = "下载超时"
        const val MESSAGE_LINK_EXPIRED = "下载链接已失效或过期（微信链接有效期 7 天，请重新申请）"
        const val MESSAGE_FAILURE = "下载失败"
        const val MESSAGE_HTTPS_ONLY = "下载失败：仅支持 https 链接"
        const val MESSAGE_TOO_MANY_REDIRECTS = "下载失败：重定向次数过多"

        /** 浏览器 UA（Chrome 稳定版字面量）；部分 CDN 拒绝无 UA 的请求。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}

/**
 * `Content-Disposition` 头解析（抽成纯函数以便 JVM 单测，不依赖网络）。
 *
 * `filename*=UTF-8''<percent-encoded>`（RFC 5987）**优先于** `filename`——
 * 微信下载响应两个参数都下发且内容一致，但 RFC 5987 形态自带字符集、无歧义，
 * 作为首选；回退 `filename` 时，若值中含 `%XX` 形态（微信实测样本的
 * `filename="%E5%BE%AE..."` 即 percent 编码的 UTF-8），一并解码。
 */
object ContentDispositionParser {

    /** 解析附件文件名；解析不出（头为空 / 无 filename 参数 / 值为空）返回 null。 */
    fun parse(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val unfolded = header.replace("\r", "").replace("\n", "")

        val extended = RFC5987_REGEX.find(unfolded)
        if (extended != null) {
            val charset = charsetOf(extended.groupValues[1])
            val value = extended.groupValues[2].trim()
            if (charset != null && value.isNotEmpty()) return decodePercent(value, charset)
        }

        val plain = PLAIN_FILENAME_REGEX.find(unfolded)
        if (plain != null) {
            val name = plain.groupValues[1].ifEmpty { plain.groupValues[2] }.trim().trim('"')
            if (name.isNotEmpty()) {
                return if (PERCENT_ENCODED_REGEX.containsMatchIn(name)) {
                    decodePercent(name, Charsets.UTF_8)
                } else {
                    name
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ 内部实现

    private fun charsetOf(token: String): Charset? = try {
        Charset.forName(token)
    } catch (e: Exception) {
        null
    }

    /** percent 解码：`%XX` → 字节后按 charset 还原字符串；非法 `%` 序列按字面量保留。 */
    private fun decodePercent(value: String, charset: Charset): String {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val c = value[index]
            if (c == '%' && index + 3 <= value.length &&
                isHexDigit(value[index + 1]) && isHexDigit(value[index + 2])
            ) {
                bytes.add(((hexValue(value[index + 1]) shl 4) or hexValue(value[index + 2])).toByte())
                index += 3
            } else {
                bytes.add(c.code.toByte())
                index += 1
            }
        }
        return String(bytes.toByteArray(), charset)
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun hexValue(c: Char): Int = Character.digit(c, 16)

    /** RFC 5987 `filename*=UTF-8''<percent-encoded>` 参数（转义用 ICU 安全写法：`[*]`、`-` 置于字符类末尾）。 */
    private val RFC5987_REGEX = Regex("""(?i)filename[*]\s*=\s*([A-Za-z0-9!*._-]+)''([^;]*)""")

    private val PLAIN_FILENAME_REGEX = Regex("""(?i)filename\s*=\s*(?:"([^"]*)"|([^;]*))""")

    /** 值中含 `%XX` 形态（判断回退 filename 是否需要 percent 解码）。 */
    private val PERCENT_ENCODED_REGEX = Regex("""%[0-9A-Fa-f]{2}""")
}
