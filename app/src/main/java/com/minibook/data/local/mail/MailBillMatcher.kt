package com.minibook.data.local.mail

import com.minibook.domain.model.BillPlatform

/** 邮箱认证失败（授权码错误/未开启 IMAP）。凭证绝不进异常信息。 */
class MailAuthException(message: String) : Exception(message)

/** 邮箱网络/超时/协议异常。凭证绝不进异常信息。 */
class MailNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 账单邮件本地过滤器（F-13，纯 Kotlin，JVM 可单测）。
 *
 * **为什么在本地过滤而不走 IMAP SEARCH**：真机 spike 实测 QQ 邮箱 IMAP 的服务端
 * SEARCH 完全不可靠（`OR(FROM "alipay")(FROM "tencent")(SUBJECT "账单")` 返回 0 封，
 * 而邮件明明存在）——**任何服务端 SEARCH 一律禁用**，只拉取窗口内近期邮件后在此判定。
 *
 * 规则（先判支付宝再判微信，两者都不中返回 null；发件人匹配大小写不敏感、需包含
 * 白名单域，可容忍 `张三 <service@mail.alipay.com>` 形态的显示名；主题为大小写不敏感
 * 的关键词包含）：
 * - 支付宝：发件人含 `service@mail.alipay.com` 且主题含 `支付宝交易流水明细`；
 * - 微信：发件人含 `wechatpay@tencent.com` 且主题含 `微信支付-账单流水文件`。
 */
class MailBillMatcher {

    /** 命中返回平台，否则 null。 */
    fun platformOf(from: String?, subject: String?): BillPlatform? {
        val sender = from?.lowercase() ?: return null
        val title = subject?.lowercase() ?: return null
        return when {
            // 顺序即优先级：先支付宝，再微信（两者规则无交集，顺序只为确定性）。
            sender.contains(ALIPAY_FROM) && title.contains(ALIPAY_SUBJECT) -> BillPlatform.ALIPAY
            sender.contains(WECHAT_FROM) && title.contains(WECHAT_SUBJECT) -> BillPlatform.WECHAT
            else -> null
        }
    }

    private companion object {
        const val ALIPAY_FROM = "service@mail.alipay.com"
        const val ALIPAY_SUBJECT = "支付宝交易流水明细"
        const val WECHAT_FROM = "wechatpay@tencent.com"
        const val WECHAT_SUBJECT = "微信支付-账单流水文件"
    }
}
