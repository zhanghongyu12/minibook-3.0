package com.minibook.domain.usecase.sms

import com.minibook.domain.model.SmsChannel

/**
 * 短信模板（F-14，设计 `docs/13_sms_bill_import.md` §3.4；DEC-025）。
 *
 * 模板 = **发送方号码** + **抬头锚点** + **渠道提示词**，**不含整句正则**：
 * 银行/支付机构的句式会随版本漂移，整句正则会静默失配；锚点 + 结构化捕获（[SmsBillParser]）
 * 把「抽不到」显式变成 `Unrecognized`（进清单留痕），而不是误记一笔。
 *
 * 同理遵循 DEC-024 的教训：**解析关键路径不使用 `java.util.regex`**（Android ICU 与 JVM
 * 语义存在差异，真机类初始化期抛错无法被 JVM 单测发现），一律手写字符扫描。
 */
data class SmsTemplate(
    /** 模板标识（留痕/审计用）。 */
    val id: String,
    /** 发送方号码前缀（空列表 = 不限发送方）。 */
    val senderPrefixes: List<String> = emptyList(),
    /** 正文必须包含的抬头锚点（空列表 = 不限；多个锚点命中任一即可）。 */
    val headerAnchors: List<String> = emptyList(),
    /** 渠道提示词 → 渠道归类（命中即用；`网银在线` 一类归 [SmsChannel.BANK_DIRECT]）。 */
    val channelHints: List<Pair<String, SmsChannel>> = emptyList(),
    /**
     * 未命中任何 [channelHints] 时的**兜底渠道**。
     *
     * ⚠️ 实测缺陷修复（2026-10-10，模拟器端到端）：兜底此前恒为 [SmsChannel.OTHER]，
     * 而 `SmsChannel.allowsAutoImport` 只认 [SmsChannel.BANK_DIRECT] —— 一条最普通的
     * 「储蓄卡…支出…余额…」银行短信（正文里没有 银联/网银在线/代扣 等提示词）会落到
     * `other`，使 L2 白名单自动记账在**最常见场景下永不触发**（静默失效，日志只有
     * 「渠道（other）会被账单文件覆盖」一句）。银行模板的兜底必须是
     * [SmsChannel.BANK_DIRECT]：正文没提第三方渠道，就是银行侧直连通知。
     */
    val defaultChannel: SmsChannel = SmsChannel.OTHER,
    /** 该模板是否可能出现账户余额（用于余额链校验）。 */
    val supportsBalance: Boolean = false,
)

/**
 * 模板注册表：发送方 + 正文 → 命中的 [SmsTemplate]（未命中返回 null → 计「未识别」）。
 *
 * **诚实说明（设计 §2.6 V4）**：当前**无真实短信账单样本**，注册表以「常见银行服务号前缀 +
 * 通用锚点」构造，只保证结构正确与保守性（抽不到就留痕，绝不猜），不代表真实覆盖率。
 * 真实样本到位后按 `发送方 × 业务类型` 增补条目，并同步补 fixture 单测。
 */
object SmsTemplateRegistry {

    /** 常见银行/支付机构服务号前缀（95xxx 段）。 */
    private val BANK_PORT_PREFIXES = listOf(
        "95526", "95588", "95533", "95555", "95566", "95559", "95577", "95561",
        "95511", "95568", "95595", "95580", "95508", "95528", "95558", "95594",
        "95599", "95501", "95530", "95313", "95337",
    )

    /**
     * 正文中出现即视为「银行侧通知」的**强锚点**。
     *
     * 刻意**不含**裸「账户」二字：否则「朋友短信里提到我的账户支付了 100 元」也会被当成账单，
     * 违背「不误记」目标（设计 §7 R9）。宁可漏判进「未识别」清单（可补模板），也不误记一笔。
     */
    private val BANK_ANCHORS = listOf(
        "银行", "信用卡", "储蓄卡", "借记卡", "尾号", "活期余额", "可用余额", "账户余额",
    )

    /** 支付平台锚点（渠道抑制的关键：这些渠道必被账单文件覆盖）。 */
    private val ALIPAY_ANCHORS = listOf("支付宝")
    private val WECHAT_ANCHORS = listOf("财付通", "微信支付", "微信零钱")

    /** 渠道提示词（顺序敏感：先具体后一般）。 */
    private val CHANNEL_HINTS = listOf(
        "支付宝" to SmsChannel.ALIPAY,
        "财付通" to SmsChannel.WECHAT,
        "微信支付" to SmsChannel.WECHAT,
        "微信零钱" to SmsChannel.WECHAT,
        "网银在线" to SmsChannel.BANK_DIRECT,
        "京东支付" to SmsChannel.BANK_DIRECT,
        "快捷支付" to SmsChannel.BANK_DIRECT,
        "银联" to SmsChannel.BANK_DIRECT,
        "代扣" to SmsChannel.BANK_DIRECT,
        "直扣" to SmsChannel.BANK_DIRECT,
        "自动还款" to SmsChannel.BANK_DIRECT,
        "取现" to SmsChannel.BANK_DIRECT,
    )

    /**
     * 匹配模板：支付平台锚点优先（渠道归类决定性），其次银行服务号/银行锚点，最后 106 端口通用。
     */
    fun match(address: String, body: String): SmsTemplate? {
        val sender = address.trim()
        if (sender.isEmpty()) return null

        // 1) 支付平台通知（支付宝/微信）：渠道抑制对象。
        if (ALIPAY_ANCHORS.any { body.contains(it) }) {
            return SmsTemplate(
                id = "payment.alipay",
                channelHints = CHANNEL_HINTS,
                supportsBalance = false,
            )
        }
        if (WECHAT_ANCHORS.any { body.contains(it) }) {
            return SmsTemplate(
                id = "payment.wechat",
                channelHints = CHANNEL_HINTS,
                supportsBalance = false,
            )
        }

        // 2) 银行服务号 / 银行锚点 → 银行卡类（允许自动记账的渠道）。
        val bankPort = BANK_PORT_PREFIXES.any { sender.startsWith(it) } ||
            (sender.length == 5 && sender.startsWith("95"))
        if (bankPort || BANK_ANCHORS.any { body.contains(it) }) {
            return SmsTemplate(
                id = "bank.generic",
                senderPrefixes = BANK_PORT_PREFIXES,
                headerAnchors = BANK_ANCHORS,
                channelHints = CHANNEL_HINTS,
                defaultChannel = SmsChannel.BANK_DIRECT, // 无第三方渠道提示词 → 银行直连（可自动记账）
                supportsBalance = true,
            )
        }

        // 3) 106 通知端口：可能是账单（其他机构），也可能是营销/验证码 —— 交由解析器判定。
        if (sender.startsWith("106")) {
            return SmsTemplate(
                id = "port.generic106",
                senderPrefixes = listOf("106"),
                channelHints = CHANNEL_HINTS,
                supportsBalance = false,
            )
        }

        return null
    }
}
