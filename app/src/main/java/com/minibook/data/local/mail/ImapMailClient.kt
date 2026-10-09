package com.minibook.data.local.mail

import android.util.Log
import com.minibook.domain.model.MailBillHeader
import com.minibook.domain.model.MailBillMail
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store

/**
 * 拉取结果：窗口内参与筛选的邮件数 + 命中的账单邮件。
 *
 * [windowMailCount] = 落在时间窗口内、参与本地筛选的邮件数（含未命中的），使上层汇总
 * 的 `totalMailCount` 不再恒等于账单命中数。
 */
data class MailFetchResult(
    val windowMailCount: Int,
    val billMails: List<MailBillMail>,
)

/**
 * QQ 邮箱 IMAP 只读客户端（F-13，真机 spike 实测结论固化）：
 *
 * 1. `imap.qq.com:993` + SSL；认证 = 邮箱地址 + 16 位 IMAP 授权码（非登录密码）。
 * 2. **必须显式发 IMAP `ID` 命令**声明客户端身份，否则 QQ 拒绝登录。双保险：
 *    属性 `mail.imap.id` / `mail.imaps.id` + 连接后 `(store as IMAPStore).id(map)`，
 *    ID 调用失败被容忍（try/catch 记录但不致命）。
 * 3. **严禁服务端 SEARCH**：实测 `OR(FROM "alipay")(FROM "tencent")(SUBJECT "账单")`
 *    返回 0 封而邮件明明存在——全程不用 `folder.search(...)`，只拉窗口内近期邮件的
 *    头部（FetchProfile），由 [MailBillMatcher] 在本地过滤，命中后才取正文/附件。
 * 4. **全程只读**：`folder.open(Folder.READ_ONLY)` + `folder.close(false)`，不写
 *    `Flags.Flag.SEEN`、不 delete/copy/append，绝不改动邮箱状态。
 * 5. 凭证（邮箱地址/授权码）只用于 `store.connect(...)`，绝不进异常信息/日志。
 */
@Singleton
class ImapMailClient @Inject constructor() {

    /**
     * 连接一次 → 拉取 sinceDays 天内邮件头部 → 本地过滤账单邮件 → 逐封取回附件与正文。
     *
     * 阻塞的 JavaMail 调用统一调度到 IO 线程。时间窗口：优先服务端 `Date` 头
     * （`getSentDate()`），为空回退 `getReceivedDate()`，两者皆空视为在窗口内（不丢件）。
     *
     * @param email QQ 邮箱地址
     * @param authCode 16 位 IMAP 授权码（非登录密码）
     * @param sinceDays 时间窗口（天），默认 30
     * @return [MailFetchResult]：窗口内参与筛选的邮件数 + 命中的账单邮件
     * @throws MailAuthException 认证失败
     * @throws MailNetworkException 网络/超时/协议异常
     */
    suspend fun fetchBillMails(email: String, authCode: String, sinceDays: Int = 30): MailFetchResult =
        withContext(Dispatchers.IO) {
            try {
                fetchBlocking(email, authCode, sinceDays)
            } catch (e: AuthenticationFailedException) {
                // AuthenticationFailedException 是 MessagingException 子类，必须先于其捕获；
                // 用固定文案，绝不透传可能携带凭证的原异常消息。
                throw MailAuthException(AUTH_FAIL_MESSAGE)
            } catch (e: SocketTimeoutException) {
                throw MailNetworkException("网络超时：无法连接或读取邮件服务器", e)
            } catch (e: MessagingException) {
                throw MailNetworkException("邮件服务器通信异常：${e.javaClass.simpleName}", e)
            } catch (e: IOException) {
                throw MailNetworkException("读取邮件内容失败：${e.javaClass.simpleName}", e)
            }
        }

    // ------------------------------------------------------------------ 连接与拉取

    private fun fetchBlocking(email: String, authCode: String, sinceDays: Int): MailFetchResult {
        val session = Session.getInstance(buildProperties(), null)
        val store = session.getStore(PROTOCOL_IMAPS)
        try {
            // 凭证只出现在这里：connect 失败的原异常消息不透传（见 fetchBillMails 映射）。
            store.connect(email, authCode)
            sendClientId(store)

            val folder = store.getFolder(INBOX)
            try {
                folder.open(Folder.READ_ONLY)
                return filterAndFetch(folder, sinceDays)
            } finally {
                // 只读关闭：false = 不 expunge（READ_ONLY 下本也无从删除）。
                if (folder.isOpen) folder.close(false)
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    /** QQ 要求的 IMAP ID 身份声明（双保险之二）；失败可容忍，不影响登录会话。 */
    private fun sendClientId(store: Store) {
        try {
            (store as? IMAPStore)?.id(CLIENT_ID_PARAMS)
        } catch (e: Exception) {
            Log.w(TAG, "IMAP ID 命令失败（可容忍，属性级 ID 已兜底）: ${e.javaClass.simpleName}")
        }
    }

    /**
     * 拉取窗口内邮件的头部 → [MailBillMatcher] 本地过滤 → 命中者才取正文/附件。
     *
     * 不使用任何服务端 SEARCH（见类 KDoc 第 3 条）；FetchProfile 只取信封头
     * （FROM/SUBJECT/Date 等）与 FLAGS，避免全量下载 136+ 封的正文。
     */
    private fun filterAndFetch(folder: Folder, sinceDays: Int): MailFetchResult {
        val messages = folder.messages
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE) // FROM / SUBJECT / Date 等信封头
            add(FetchProfile.Item.FLAGS)
        }
        folder.fetch(messages, profile)

        val cutoff = System.currentTimeMillis() - sinceDays * MILLIS_PER_DAY
        val matcher = MailBillMatcher()
        val result = ArrayList<MailBillMail>()
        var windowMailCount = 0
        for (message in messages) {
            // 窗口判断：优先 Date 头（sentDate），回退收件时间；皆空则视为在窗口内（不丢件）。
            val date = message.sentDate ?: message.receivedDate
            if (date != null && date.time < cutoff) continue
            windowMailCount++ // 落在窗口内、参与筛选的邮件（含未命中者）

            val platform = matcher.platformOf(
                from = message.from?.firstOrNull()?.toString(),
                subject = message.subject,
            ) ?: continue

            val header = MailBillHeader(
                platform = platform,
                subject = message.subject ?: "",
                sentDateMillis = date?.time ?: 0L,
                messageNumber = message.messageNumber,
            )
            val extracted = ExtractedContent().also { walkPart(message, it) }
            result.add(
                MailBillMail(
                    header = header,
                    attachmentFileName = extracted.attachmentFileName, // 原样未解码（RFC2047/RFC2231）
                    attachmentBytes = extracted.attachmentBytes,
                    bodyText = extracted.bodyText(),
                ),
            )
        }
        return MailFetchResult(windowMailCount = windowMailCount, billMails = result)
    }

    // ------------------------------------------------------------------ 正文/附件抽取

    /**
     * 单封邮件抽取的中间结果（附件取首个命中，正文 plain 与 html 两路都收集）。
     *
     * 微信账单邮件是 `multipart/ALTERNATIVE`，下载链接**只存在于 HTML 部分的
     * `<a href="...">` 里**，纯文本部分只有「点击下载」四个字、没有 URL——
     * 因此正文必须两路拼接而非 plain 优先，否则链接必丢。
     */
    private class ExtractedContent {
        var attachmentFileName: String? = null
        var attachmentBytes: ByteArray? = null
        var plainText: String? = null
        var htmlText: String? = null

        /** plain 与 html 两路非空白结果的 `\n` 拼接；对后续「扫链接」无害。 */
        fun bodyText(): String = listOfNotNull(plainText, htmlText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /** 递归遍历 Multipart：附件 mime 命中即取，正文分 text/plain / text/html 收集。 */
    private fun walkPart(part: Part, out: ExtractedContent) {
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as? Multipart ?: return
            for (index in 0 until multipart.count) {
                walkPart(multipart.getBodyPart(index), out)
            }
            return
        }
        if (out.attachmentFileName == null && isAttachmentMime(part)) {
            out.attachmentFileName = part.fileName // 原始串，不解码（后续任务专门处理）
            out.attachmentBytes = part.inputStream.use { it.readBytes() }
            return
        }
        when {
            out.plainText == null && part.isMimeType("text/plain") ->
                out.plainText = readText(part)
            out.htmlText == null && part.isMimeType("text/html") -> {
                val raw = readText(part)
                out.htmlText = stripHtml(raw)
            }
        }
    }

    private fun isAttachmentMime(part: Part): Boolean =
        part.isMimeType("application/octet-stream") ||
            part.isMimeType("application/zip") ||
            part.isMimeType("application/x-zip-compressed")

    /** getContent() 通常是已按 MIME charset 解码的 String；否则回退读原始字节流（按 UTF-8 解释）。 */
    private fun readText(part: Part): String =
        (part.content as? String) ?: part.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    /** HTML 去标签（URL 保留）统一委托 [MailHtmlText]，避免两套实现漂移。 */
    private fun stripHtml(html: String): String = MailHtmlText.toPlainText(html)

    // ------------------------------------------------------------------ 配置

    private fun buildProperties(): Properties = Properties().apply {
        put("mail.imaps.host", HOST)
        put("mail.imaps.port", PORT.toString())
        put("mail.imaps.ssl.enable", "true") // spike 结论：SSL 必须开
        put("mail.imaps.connectiontimeout", TIMEOUT_MS.toString())
        put("mail.imaps.timeout", TIMEOUT_MS.toString())
        // ID 双保险之一：属性级身份声明（连接协商阶段生效）。
        put("mail.imap.id", CLIENT_ID_PROPERTY_VALUE)
        put("mail.imaps.id", CLIENT_ID_PROPERTY_VALUE)
    }

    private companion object {
        const val TAG = "ImapMailClient"
        const val PROTOCOL_IMAPS = "imaps"
        const val HOST = "imap.qq.com"
        const val PORT = 993
        const val INBOX = "INBOX"
        const val TIMEOUT_MS = 30_000
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val AUTH_FAIL_MESSAGE = "登录失败：请确认已在 QQ 邮箱开启 IMAP/SMTP 服务，且授权码为最新"

        /** IMAP ID 参数（spike 实测：QQ 要求声明客户端身份，否则拒绝登录）。 */
        val CLIENT_ID_PARAMS: Map<String, String> = mapOf(
            "name" to "MiniBook",
            "version" to "1.0",
        )

        /** 属性形态的 ID 声明（IMAP ID 响应格式字符串）。 */
        const val CLIENT_ID_PROPERTY_VALUE = """("name" "MiniBook" "version" "1.0" "vendor" "MiniBook")"""
    }
}
