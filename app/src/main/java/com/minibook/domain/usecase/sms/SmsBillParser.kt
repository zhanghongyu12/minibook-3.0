package com.minibook.domain.usecase.sms

import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.SmsChannel
import com.minibook.domain.model.SmsMessageRow
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.TransactionType
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/**
 * 短信账单解析器（F-14，设计 §3.4；DEC-025）。
 *
 * **三态输出**（[SmsParseOutcome]）：`Parsed` / `Unrecognized`（疑似账单但抽不全 → 留痕）/
 * `NotBill`（判定非账单 → 只记依据）。**保守优先**：任何字段抽不到宁可 `Unrecognized`，绝不猜。
 *
 * 实现约束：
 * - **零正则**（DEC-024：Android ICU 与 JVM 语义差异会在真机类初始化期炸，JVM 单测覆盖不到）；
 * - 纯 Kotlin、无 `android.*` → JVM 可完整单测；
 * - `date` 口径与 F-12 一致（`yyyy-MM-dd`，**时分秒丢弃**，设计 §2.4）；
 * - `externalId = sms:<sha256(address|date|规范化正文)[:16]>`（短信普遍无交易单号）；
 * - **原文不落库**（DEC-026）：`description` 为自建摘要，不含短信全文。
 */
object SmsBillParser {

    /** 支出词表（命中即支出）。 */
    private val EXPENSE_KEYWORDS = listOf(
        "支付", "消费", "扣款", "支出", "取现", "转出", "还款", "代扣", "退款",
    )

    /** 收入词表（命中即收入）。 */
    private val INCOME_KEYWORDS = listOf(
        "入账", "到账", "收入", "存入", "转入", "收款", "发放", "利息",
    )

    /** 非账单：验证码/安全类。 */
    private val NOT_BILL_SECURITY = listOf(
        "验证码", "校验码", "动态密码", "动态码", "登录", "密码", "安全提醒",
    )

    /** 非账单：营销/服务类。 */
    private val NOT_BILL_MARKETING = listOf(
        "优惠", "活动", "促销", "积分", "权益", "抽奖", "退订", "快递", "取件", "派送",
        "话费", "流量", "停机", "欠费", "套餐", "账单已出", "还款提醒", "尊敬的客户您好",
    )

    /** 余额上下文词（出现在金额数字之前 → 判为余额而非交易金额）。 */
    private val BALANCE_CONTEXT = listOf("余额", "可用", "额度", "可用额度")

    /** 对方名称锚点。 */
    private val COUNTERPARTY_ANCHORS = listOf(
        "对方户名:", "对方户名：", "对方:", "对方：", "商户:", "商户：", "收款方:", "收款方：", "户名:",
    )

    /** 渠道词（用于 `paymentMethod` 与摘要）。 */
    private val CHANNEL_WORDS = listOf(
        "网银在线", "支付宝", "财付通", "微信支付", "微信零钱", "京东支付", "快捷支付", "银联", "代扣",
    )

    private const val CONFIDENCE_FULL = 3
    private const val CONFIDENCE_BASIC = 2
    private const val CONFIDENCE_WEAK = 1

    /** 自动记账所需的最低置信度（设计 §3.8 条件②）。 */
    const val AUTO_IMPORT_MIN_CONFIDENCE = CONFIDENCE_BASIC

    /**
     * 解析单条短信。
     *
     * @param template [SmsTemplateRegistry.match] 的命中结果（null 表示发送方未登记）
     * @param zoneId 用于把「到达时间戳」还原为日期参考年（默认系统时区）
     */
    fun parse(
        message: SmsMessageRow,
        template: SmsTemplate?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SmsParseOutcome {
        if (template == null) {
            return SmsParseOutcome.Unrecognized(reason = "发送方未登记模板")
        }

        val body = message.body
        if (body.isBlank()) {
            return SmsParseOutcome.NotBill(reason = "空短信")
        }

        val expenseHit = firstHit(body, EXPENSE_KEYWORDS)
        val incomeHit = firstHit(body, INCOME_KEYWORDS)

        // 方向必须**唯一确定**：两侧都命中时按更靠前出现者判定（位置相同视为歧义）。
        val type: TransactionType = when {
            expenseHit != null && incomeHit == null -> TransactionType.EXPENSE
            incomeHit != null && expenseHit == null -> TransactionType.INCOME
            expenseHit != null && incomeHit != null -> when {
                expenseHit.first < incomeHit.first -> TransactionType.EXPENSE
                incomeHit.first < expenseHit.first -> TransactionType.INCOME
                else -> return SmsParseOutcome.Unrecognized(reason = "收支方向歧义")
            }
            else -> {
                // 无方向词：可能是非账单（验证码/营销），也可能是句式变化 → 区分留痕。
                val security = firstHit(body, NOT_BILL_SECURITY)
                if (security != null) return SmsParseOutcome.NotBill(reason = "安全/验证码类：${security.second}")
                val marketing = firstHit(body, NOT_BILL_MARKETING)
                if (marketing != null) return SmsParseOutcome.NotBill(reason = "营销/服务类：${marketing.second}")
                return SmsParseOutcome.Unrecognized(reason = "未找到收支方向词")
            }
        }

        // 金额：取第一个「元」前的数字，且其前缀不能是余额上下文（余额另存用于链条校验）。
        val amountScan = scanAmounts(body)
        val amountFen = amountScan.transactionFen
            ?: return SmsParseOutcome.Unrecognized(
                reason = if (amountScan.balanceFen != null) "只找到余额金额、无交易金额" else "未找到交易金额",
            )
        if (amountFen <= 0L) {
            return SmsParseOutcome.Unrecognized(reason = "金额非法：$amountFen")
        }

        val receivedAt = Instant.ofEpochMilli(message.receivedAtMs).atZone(zoneId)
        val date = extractDate(body, receivedAt.year, receivedAt.monthValue)
            ?: return SmsParseOutcome.Unrecognized(reason = "未找到交易日期")

        val channel = resolveChannel(body, template)
        val cardTail = extractCardTail(body)
        val counterparty = extractCounterparty(body)
        val channelWord = CHANNEL_WORDS.firstOrNull { body.contains(it) }

        val confidence = confidenceOf(
            hasTime = extractTime(body) != null,
            hasBalance = amountScan.balanceFen != null,
            hasChannelWord = channelWord != null,
        )

        val description = buildDescription(type, channelWord, cardTail)

        val row = ParsedBillRow(
            date = date,
            type = type,
            amountFen = amountFen,
            counterparty = counterparty,
            paymentMethod = channelWord,
            description = description,
            externalId = externalIdOf(message.address, date, body),
            platformCategory = channelWord,
        )

        return SmsParseOutcome.Parsed(
            row = row,
            channel = channel,
            cardTail = cardTail,
            balanceFen = amountScan.balanceFen,
            confidence = confidence,
            sender = message.address,
            receivedAtMs = message.receivedAtMs,
            contentHash = message.contentHash,
        )
    }

    // ---------------------------------------------------------------- 抽取实现（手写扫描）

    /** 关键词命中：返回（首次出现下标, 命中词）。 */
    private fun firstHit(text: String, keywords: List<String>): Pair<Int, String>? {
        var best: Pair<Int, String>? = null
        for (k in keywords) {
            val idx = text.indexOf(k)
            if (idx >= 0 && (best == null || idx < best.first)) best = idx to k
        }
        return best
    }

    /** 金额扫描结果：交易金额 + 余额（可空）。 */
    internal data class AmountScan(val transactionFen: Long?, val balanceFen: Long?)

    /**
     * 扫描所有「…元」：紧邻「元」的数字为候选金额；
     * 若数字前缀 6 字内出现余额上下文词 → 记为余额，否则记为交易金额（取首个）。
     */
    internal fun scanAmounts(body: String): AmountScan {
        var transactionFen: Long? = null
        var balanceFen: Long? = null
        var from = 0
        while (true) {
            val idx = body.indexOf('元', from)
            if (idx < 0) break
            from = idx + 1
            val numberStart = numberStartBefore(body, idx) ?: continue
            val raw = body.substring(numberStart, idx)
            val fen = fenOf(raw) ?: continue
            val prefix = body.substring(maxOf(0, numberStart - 6), numberStart)
            val isBalance = BALANCE_CONTEXT.any { prefix.contains(it) }
            if (isBalance) {
                if (balanceFen == null) balanceFen = fen
            } else if (transactionFen == null) {
                transactionFen = fen
            }
        }
        return AmountScan(transactionFen, balanceFen)
    }

    /** 返回紧邻 [endExclusive] 左侧的金额数字串的起始下标（允许千分位逗号与小数点）。 */
    private fun numberStartBefore(text: String, endExclusive: Int): Int? {
        var i = endExclusive - 1
        var seenDigit = false
        while (i >= 0) {
            val c = text[i]
            when {
                c in '0'..'9' -> {
                    seenDigit = true
                    i--
                }
                (c == '.' || c == ',') && seenDigit && i - 1 >= 0 && text[i - 1] in '0'..'9' -> i--
                else -> break
            }
        }
        return if (seenDigit) i + 1 else null
    }

    /** "1,234.50" / "1234.5" → 分；非法返回 null。 */
    internal fun fenOf(raw: String): Long? {
        val cleaned = raw.replace(",", "")
        if (cleaned.isEmpty() || cleaned == ".") return null
        val dot = cleaned.indexOf('.')
        val intPart: String
        val fracPart: String
        if (dot < 0) {
            intPart = cleaned
            fracPart = ""
        } else {
            intPart = cleaned.substring(0, dot)
            fracPart = cleaned.substring(dot + 1)
        }
        if (intPart.isEmpty() || intPart.any { it !in '0'..'9' }) return null
        val frac = when {
            fracPart.isEmpty() -> 0L
            fracPart.length == 1 -> fracPart.toLong() * 10
            fracPart.length == 2 -> fracPart.toLong()
            else -> fracPart.substring(0, 2).toLong()
        }
        if (fracPart.length > 2 && fracPart.drop(2).any { it != '0' }) return null // 多于两位小数 → 非法
        val yuan = intPart.toLongOrNull() ?: return null
        return yuan * 100 + frac
    }

    /** 抽取 `M月D日`（年份取短信到达年的参考年；跨年短信回退一年）。 */
    internal fun extractDate(body: String, refYear: Int, refMonth: Int): String? {
        val monthIdx = body.indexOf('月')
        if (monthIdx <= 0) return null
        val monthStart = numberStartBefore(body, monthIdx) ?: return null
        val monthStr = body.substring(monthStart, monthIdx)
        val month = monthStr.toIntOrNull() ?: return null
        if (month !in 1..12) return null

        val dayIdx = body.indexOf('日', monthIdx)
        if (dayIdx < 0) return null
        val dayStartRel = numberStartBefore(body, dayIdx) ?: return null
        if (dayStartRel < monthIdx) return null // 「日」前的数字必须在「月」之后（防跨段误取）
        val dayStr = body.substring(dayStartRel, dayIdx)
        val day = dayStr.toIntOrNull() ?: return null
        if (day !in 1..31) return null

        // 短信无年份：以后到达时间为参考；解析出的月份大于参考月 → 判为去年（跨年短信）。
        val year = if (month > refMonth) refYear - 1 else refYear
        return "%04d-%02d-%02d".format(year, month, day)
    }

    /** 抽取 `H:mm` / `HH:mm` / `HH时mm分`（仅用于置信度判断，不落库）。 */
    internal fun extractTime(body: String): String? {
        val colon = body.indexOf(':')
        if (colon > 0) {
            val start = numberStartBefore(body, colon) ?: return null
            val hour = body.substring(start, colon).toIntOrNull() ?: return null
            var end = colon + 1
            val sb = StringBuilder()
            while (end < body.length && body[end] in '0'..'9' && sb.length < 2) {
                sb.append(body[end])
                end++
            }
            if (sb.isNotEmpty() && hour in 0..23) return "$hour:${sb.toString().padStart(2, '0')}"
        }
        val shi = body.indexOf('时')
        if (shi > 0) {
            val start = numberStartBefore(body, shi) ?: return null
            val hour = body.substring(start, shi).toIntOrNull() ?: return null
            if (hour in 0..23 && body.substring(shi).contains('分')) {
                val fenIdx = body.indexOf('分', shi)
                val mStart = numberStartBefore(body, fenIdx) ?: return null
                val minute = body.substring(mStart, fenIdx).toIntOrNull() ?: return null
                if (minute in 0..59) return "$hour:${minute.toString().padStart(2, '0')}"
            }
        }
        return null
    }

    /** 卡尾号：`尾号1234` / `账户1234` / `(1234)`。 */
    internal fun extractCardTail(body: String): String? {
        for (anchor in listOf("尾号", "账户", "卡号")) {
            val idx = body.indexOf(anchor)
            if (idx < 0) continue
            var i = idx + anchor.length
            // 允许 `尾号:` / `尾号：` / `尾号 ` 等分隔
            while (i < body.length && body[i] !in '0'..'9' && i - (idx + anchor.length) < 3) i++
            var digits = StringBuilder()
            while (i < body.length && body[i] in '0'..'9' && digits.length < 6) {
                digits.append(body[i])
                i++
            }
            if (digits.length >= 4) return digits.toString().takeLast(4)
        }
        return null
    }

    /** 对方名称（商户/户名），抽不到返回 null（不猜）。 */
    internal fun extractCounterparty(body: String): String? {
        for (anchor in COUNTERPARTY_ANCHORS) {
            val idx = body.indexOf(anchor)
            if (idx < 0) continue
            var end = idx + anchor.length
            val sb = StringBuilder()
            while (end < body.length && sb.length < 40) {
                val c = body[end]
                if (c == '。' || c == '\n' || c == '；' || c == ';' || c == '，' || c == ',') break
                sb.append(c)
                end++
            }
            val value = sb.toString().trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    /** 渠道归类：模板提示词优先，其次正文渠道词。 */
    internal fun resolveChannel(body: String, template: SmsTemplate): SmsChannel {
        for ((word, channel) in template.channelHints) {
            if (body.contains(word)) return channel
        }
        // 兜底渠道由模板决定（实测缺陷修复 2026-10-10）：银行模板兜底 BANK_DIRECT，
        // 否则一条无渠道提示词的普通银行消费短信会被误判 `other` 而永不自动记账。
        return template.defaultChannel
    }

    private fun confidenceOf(hasTime: Boolean, hasBalance: Boolean, hasChannelWord: Boolean): Int {
        var score = CONFIDENCE_WEAK
        if (hasTime) score++
        if (hasBalance) score++
        if (hasChannelWord) score++
        return minOf(score, CONFIDENCE_FULL)
    }

    /** 摘要（≤200 字，**不含短信全文**；DEC-026 原文不落库）。 */
    private fun buildDescription(
        type: TransactionType,
        channelWord: String?,
        cardTail: String?,
    ): String {
        val kind = when (type) {
            TransactionType.EXPENSE -> "短信消费"
            TransactionType.INCOME -> "短信入账"
        }
        val parts = mutableListOf(kind)
        cardTail?.let { parts += "尾号$it" }
        channelWord?.let { parts += it }
        return parts.joinToString("·")
    }

    /**
     * `externalId`：`sms:<sha256(address|yyyy-MM-dd|规范化正文)[:16]>`。
     *
     * 短信普遍**没有**交易单号（设计 §2.5），故以「发送方 + 日期 + 规范化正文」为幂等键；
     * 规范化 = 去零宽字符 + 全角转半角 + 空白折叠 + 首尾去空白（同一短信重投递时哈希稳定）。
     */
    fun externalIdOf(address: String, date: String, body: String): String {
        val normalized = normalizeForHash(body)
        val payload = "$address|$date|$normalized"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            for (b in digest) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
        return "sms:${hex.substring(0, 16)}"
    }

    private const val HEX = "0123456789abcdef"

    /** 规范化：零宽字符（200B~200D/FEFF）剔除、全角 ASCII 转半角、空白折叠、首尾裁剪。 */
    internal fun normalizeForHash(body: String): String {
        val sb = StringBuilder(body.length)
        var lastWasSpace = false
        for (c in body) {
            val ch = when {
                c.code == 0x200B || c.code == 0x200C || c.code == 0x200D || c.code == 0xFEFF ||
                    c.code == 0x2060 -> continue
                c.code == 0x3000 -> ' '
                c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
                else -> c
            }
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                if (!lastWasSpace && sb.isNotEmpty()) sb.append(' ')
                lastWasSpace = true
            } else {
                sb.append(ch)
                lastWasSpace = false
            }
        }
        return sb.toString().trim()
    }

    /** UI 展示用的脱敏摘要（不返回原文；金额与渠道可展示，长号码收敛）。 */
    fun previewOf(message: SmsMessageRow, maxLen: Int = 40): String {
        val body = normalizeForHash(message.body)
        val amount = scanAmounts(body).transactionFen?.let { fen ->
            "金额 ${fen / 100}.${(fen % 100).toString().padStart(2, '0')} 元"
        }
        val head = body.take(maxLen)
        return if (amount != null) "$amount ｜ $head" else head
    }
}
