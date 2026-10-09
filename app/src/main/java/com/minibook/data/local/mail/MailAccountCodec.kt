package com.minibook.data.local.mail

import com.minibook.domain.model.MailAccount

/**
 * 邮箱账号列表 JSON 编解码 + 输入校验（F-13 多账号增强，纯 JVM 可单测，无 Android 依赖）。
 *
 * **手写解析，刻意不使用正则**：Android 的 `java.util.regex` 底层是 ICU，与 JDK 正则
 * 存在行为差异（历史上 `\}` 转义曾在真机 `<clinit>` 抛 `PatternSyntaxException`，而
 * JVM 单测全绿），因此本类的解析与校验全部为字符级扫描实现，不依赖任何正则引擎。
 *
 * JSON 格式（落盘 `minibook_mail_accounts.json`）：
 * `[{"id":"<uuid>","email":"xxx@example.com"}]`——**手写编解码**，不引入 `org.json`
 * （其 android.jar 桩在 JVM 单测会抛 Stub 异常）或任何三方依赖。
 *
 * 容错纪律：[decode] 对 null/空白/非法 JSON 一律返回空列表，绝不抛异常（读盘损坏不致命）；
 * 条目缺 id 或缺邮箱地址时丢弃该条目；id 重复时保留首见条目。
 *
 * 校验口径沿用单账号时代规则：邮箱须匹配基本格式（含 `@` 且域名含 `.`）；
 * 授权码 trim 后必须恰为 [MailAccountStore.AUTH_CODE_LENGTH] 位。
 */
internal object MailAccountCodec {

    /** 换页符（U+000C）：JSON `\f` 对应字符。仅解码防御分支使用。 */
    private val FORM_FEED = 12.toChar()

    /** 条目键名 `"id"`（含两侧引号，与旧正则口径一致：键序固定 id 在前、email 在后）。 */
    private const val KEY_ID = "\"id\""

    /** 条目键名 `"email"`（含两侧引号）。 */
    private const val KEY_EMAIL = "\"email\""

    /**
     * 账号列表 → JSON 字符串（字符串值经 [escapeJson] 转义，保证往返无损）。
     * 空列表编码为 `[]`。
     */
    fun encode(accounts: List<MailAccount>): String =
        accounts.joinToString(separator = ",", prefix = "[", postfix = "]") { account ->
            """{"id":"${escapeJson(account.id)}","email":"${escapeJson(account.email)}"}"""
        }

    /**
     * JSON 字符串 → 账号列表。null/空白/非法 JSON → 空列表（不抛）；条目缺 id/邮箱丢弃；
     * id 重复保留首见。整个解析包 `runCatching`，任何意外（如结构异常）都折叠为空列表。
     *
     * 实现为字符级扫描（非正则）：逐字符找 `{` 作为条目起点，按
     * `"id" : <string> , "email" : <string> }` 的固定骨架扫描；任何一环不符即丢弃该
     * 条目并从下一个字符继续找，天然容忍条目外的任意垃圾文本（与旧正则 findAll 口径一致）。
     */
    fun decode(json: String?): List<MailAccount> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { parseAccounts(json) }.getOrDefault(emptyList())
    }

    /**
     * 邮箱地址格式校验（基本格式：含 `@` 且域名含 `.`，不含空白；入参应已 trim）。
     * 手写判断，与旧正则 `^[^@\s]+@[^@\s]+\.[^@\s]+$` 等价：
     * 恰一个 `@`、本地名非空、无空白、域名中存在一个前后都至少还有一个字符的 `.`。
     */
    fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) return false
        val atIndex = email.indexOf('@')
        if (atIndex <= 0) return false // 缺 `@` 或本地名为空
        if (email.indexOf('@', atIndex + 1) >= 0) return false // 多个 `@`
        if (email.any { it.isWhitespace() }) return false
        val domain = email.substring(atIndex + 1)
        if (domain.isEmpty()) return false
        // 域名须含 `.` 且点前、点后都至少一个字符（`a.b`、`a.b.c` 合法；`.com`、`com.` 不合法）。
        return domain.indices.any { index ->
            domain[index] == '.' && index > 0 && index < domain.length - 1
        }
    }

    /** IMAP 授权码校验：trim 后必须恰为 [MailAccountStore.AUTH_CODE_LENGTH] 位。 */
    fun validateAuthCode(code: String): Boolean = code.trim().length == MailAccountStore.AUTH_CODE_LENGTH

    // ------------------------------------------------------------------ 手写解析

    /** 一个解析成功的条目：[endIndex] 为闭括号 `}` 之后的位置。 */
    private class ParsedEntry(val endIndex: Int, val id: String, val email: String)

    /** 一个解析成功的 JSON 字符串：[endIndex] 为闭引号之后的位置，[value] 已反转义。 */
    private class ParsedString(val endIndex: Int, val value: String)

    /**
     * 扫描全文提取全部合法条目。条目骨架：`{ ws "id" ws : ws <str> ws , ws "email" ws : ws <str> ws }`。
     * 无法解析的 `{` 只跳过一个字符继续向后扫描（旧正则同样允许条目嵌在任意文本中）。
     */
    private fun parseAccounts(json: String): List<MailAccount> {
        val accounts = ArrayList<MailAccount>()
        val seen = HashSet<String>()
        var index = 0
        while (index < json.length) {
            if (json[index] != '{') {
                index++
                continue
            }
            val entry = parseEntry(json, index)
            if (entry == null) {
                index++ // 该 `{` 不是合法条目起点：跳过它自身继续找
                continue
            }
            val id = entry.id.trim()
            val email = entry.email.trim()
            // 缺 id/邮箱丢弃；id 去重保留首见（与旧正则实现口径一致）。
            if (id.isNotEmpty() && email.isNotEmpty() && seen.add(id)) {
                accounts.add(MailAccount(id, email))
            }
            index = entry.endIndex
        }
        return accounts
    }

    /** 解析单个条目；任何一环不符返回 null（条目整体丢弃）。[start] 指向 `{`。 */
    private fun parseEntry(json: String, start: Int): ParsedEntry? {
        var i = skipWhitespace(json, start + 1)
        i = expectKey(json, i, KEY_ID) ?: return null
        i = expectColon(json, i) ?: return null
        val id = parseString(json, i) ?: return null
        i = skipWhitespace(json, id.endIndex)
        if (i >= json.length || json[i] != ',') return null
        i = skipWhitespace(json, i + 1)
        i = expectKey(json, i, KEY_EMAIL) ?: return null
        i = expectColon(json, i) ?: return null
        val email = parseString(json, i) ?: return null
        i = skipWhitespace(json, email.endIndex)
        if (i >= json.length || json[i] != '}') return null
        return ParsedEntry(i + 1, id.value, email.value)
    }

    /** 位置 [i] 起须为 [key]（如 `"id"`），返回键名之后的下一个待读位置；不符返回 null。 */
    private fun expectKey(json: String, i: Int, key: String): Int? {
        if (!json.startsWith(key, i)) return null
        return skipWhitespace(json, i + key.length)
    }

    /** 位置 [i] 起须为 `:`，返回冒号之后的下一个待读位置；不符返回 null。 */
    private fun expectColon(json: String, i: Int): Int? {
        if (i >= json.length || json[i] != ':') return null
        return skipWhitespace(json, i + 1)
    }

    /** 跳过 [i] 起的连续空白字符，返回第一个非空白位置（可能为 [json] 末尾）。 */
    private fun skipWhitespace(json: String, i: Int): Int {
        var index = i
        while (index < json.length && json[index].isWhitespace()) index++
        return index
    }

    /**
     * 解析 JSON 字符串字面量（位置 [i] 须为开引号 `"`）。值内容按
     * 「非引号非反斜杠字符，或反斜杠 + 任意字符」逐对收集（与旧正则值捕获组口径一致），
     * 再经 [unescapeJson] 反转义。未闭合（找不到不带转义的闭引号）返回 null。
     */
    private fun parseString(json: String, i: Int): ParsedString? {
        if (i >= json.length || json[i] != '"') return null
        val raw = StringBuilder()
        var index = i + 1
        while (index < json.length) {
            val ch = json[index]
            if (ch == '"') return ParsedString(index + 1, unescapeJson(raw.toString()))
            if (ch == '\\') {
                if (index + 1 >= json.length) return null // 末尾悬空反斜杠：字符串未闭合
                raw.append(ch).append(json[index + 1]) // 转义对原样保留，统一交给 unescapeJson
                index += 2
            } else {
                raw.append(ch)
                index++
            }
        }
        return null
    }

    // ------------------------------------------------------------------ JSON 转义

    /**
     * 字符串值 → JSON 字面量内容：`\`、`"`、常见控制字符转义；其余 < 0x20 的控制字符
     * 统一 `\uXXXX` 转义（含换页符，不逐个列分支）。
     */
    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }

    /** JSON 字面量内容 → 原字符串；仅支持 [escapeJson] 产出的转义集合，其余原样保留。 */
    private fun unescapeJson(value: String): String {
        if (!value.contains('\\')) return value
        return buildString(value.length) {
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch != '\\') {
                    append(ch)
                    i++
                    continue
                }
                when (val next = value.getOrNull(i + 1)) {
                    '\\' -> { append('\\'); i += 2 }
                    '"' -> { append('"'); i += 2 }
                    'n' -> { append('\n'); i += 2 }
                    'r' -> { append('\r'); i += 2 }
                    't' -> { append('\t'); i += 2 }
                    'b' -> { append('\b'); i += 2 }
                    'f' -> { append(FORM_FEED); i += 2 } // 防御分支：本类编码已不产出 \f
                    'u' -> {
                        val hex = value.substring(i + 2, (i + 6).coerceAtMost(value.length))
                        val code = hex.toIntOrNull(16)
                        if (hex.length == 4 && code != null) {
                            append(code.toChar())
                            i += 6
                        } else {
                            append(ch)
                            i++
                        }
                    }
                    else -> { append(ch); i++ }
                }
            }
        }
    }
}
