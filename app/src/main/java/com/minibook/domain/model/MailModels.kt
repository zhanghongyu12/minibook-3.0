package com.minibook.domain.model

/**
 * 账单来源平台（F-13 邮件账单导入，PRD §F-13）。
 *
 * 由 [com.minibook.data.local.mail.MailBillMatcher] 依据发件人白名单域 + 主题关键词
 * 在本地判定；ALIPAY/WECHAT 分别对应支付宝、微信支付的账单邮件。
 */
enum class BillPlatform { ALIPAY, WECHAT }

/**
 * 已配置的邮箱凭据账号（F-13 多账号增强，**不含授权码本身**，授权码只在加密文件里）。
 *
 * @property id 账号唯一标识（UUID，`MailAccountStore` 生成后不变，用于定位各自的授权码密文文件）
 * @property email 邮箱地址（明文存储，非敏感）
 */
data class MailAccount(val id: String, val email: String)

/**
 * 一封命中账单特征的邮件摘要（F-13）。
 *
 * 仅含过滤/展示所需字段，不携带正文与附件；[messageNumber] 为 IMAP 会话内
 * 文件夹的序号（仅调试定位用，会话关闭后失效）。
 *
 * @property platform 命中的账单平台
 * @property subject 原始主题（已由 JavaMail 按 MIME 规范解码）
 * @property sentDateMillis 发送时间毫秒；服务端缺 Date 头时回退收件时间，两者皆缺为 0
 * @property messageNumber IMAP 文件夹内的邮件序号
 */
data class MailBillHeader(
    val platform: BillPlatform,
    val subject: String,
    val sentDateMillis: Long,
    val messageNumber: Int,
)

/**
 * 一封账单邮件取全后的内容（F-13）：附件与正文二者至少其一有值。
 *
 * 保留 data class（为 `copy()`/解构生成的 `componentN` 便利），但 **equals/hashCode
 * 手动重写**：编译器默认实现按引用比较 [attachmentBytes]（ByteArray 不重写
 * contentEquals 语义），会把内容相同的两封邮件判为不等，故改为按数组内容比较。
 *
 * @property header 命中摘要（平台/主题/时间/序号）
 * @property attachmentFileName 附件原始文件名（**未解码**，可能形如 `=?GB18030?Q?...?=`
 *   的 RFC2047/RFC2231 原始串）；无附件为 null。解码由后续任务专门处理，此处原样透传
 * @property attachmentBytes 附件字节流；无附件为 null
 * @property bodyText 正文文本：text/plain 与 text/html（朴素去标签、**保留 href 里的 URL**）
 *   两路非空白结果以 `\n` 拼接——微信下载链接只存在于 HTML 路，拼接避免丢失；无正文为 ""
 */
data class MailBillMail(
    val header: MailBillHeader,
    val attachmentFileName: String?,
    val attachmentBytes: ByteArray?,
    val bodyText: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MailBillMail) return false
        return header == other.header &&
            attachmentFileName == other.attachmentFileName &&
            bodyText == other.bodyText &&
            attachmentBytes.contentEquals(other.attachmentBytes)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + (attachmentFileName?.hashCode() ?: 0)
        result = 31 * result + (attachmentBytes?.contentHashCode() ?: 0)
        result = 31 * result + bodyText.hashCode()
        return result
    }
}
