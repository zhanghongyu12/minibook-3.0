package com.minibook.data.local.mail

/**
 * HTML → 纯文本（保留 `href`/`src` 里的 URL）。纯 Kotlin，无 Android 依赖，可 JVM 单测。
 *
 * **刻意手写字符扫描、零正则**：Android 的 `java.util.regex` 底层是 ICU，与 JDK 语义
 * 存在差异（历史上 `\}` 曾在真机 `<clinit>` 抛 `PatternSyntaxException`；同一套
 * 「带 URL 开始标签」正则在 JDK 命中、真机却 NoLink）。本类处于 F-13 微信账单
 * 取链关键路径，彻底不依赖正则，语义与旧正则实现保持一致。
 *
 * 顺序至关重要（微信账单下载链接丢失 bug 的修法）：
 * 1. **先**把「带 `href`/`src` 属性的开始标签」整体替换为其 URL——URL 落到任何标签
 *    **之外**，后续去标签才删不掉它。微信账单邮件是 `multipart/ALTERNATIVE`，下载链接
 *    只存在于 HTML 部分的 `<a href="https://tenpay.wechatpay.cn/...">点击下载</a>` 里，
 *    若先按去标签规则整体删除，整个标签连同 URL 一起被吃掉，正文只剩「点击下载」四字。
 * 2. 再删**剩余**标签（→ 空格，避免相邻块文本粘连）。
 * 3. 反转义常见实体，`&amp;` **最后**替换（防止二次反转义，如 `&amp;lt;` → `&lt;` 而非 `<`）。
 */
internal object MailHtmlText {

    /** HTML → 纯文本：URL 保留在标签外，标签删除，实体反转义，首尾去空白。 */
    fun toPlainText(html: String): String {
        val out = StringBuilder(html.length)
        var i = 0
        while (i < html.length) {
            if (html[i] != '<') {
                out.append(html[i])
                i++
                continue
            }
            val kind = tagKindAt(html, i)
            if (kind == TagKind.NOT_TAG) {
                // 普通文本里的 `<`（如 `a < b`）：不是标签，原样保留。
                out.append('<')
                i++
                continue
            }
            val end = findTagEnd(html, i)
            if (end < 0) break // 无闭合 `>`：丢弃剩余半个标签，保持输出确定性
            val tag = html.substring(i, end + 1)
            // 仅开始标签可能携带 href/src；命中则「整个标签 → " URL "」，URL 落到标签外。
            val url = if (kind == TagKind.START) findUrlAttribute(tag) else null
            if (url != null) {
                out.append(' ').append(url).append(' ')
            } else {
                out.append(' ')
            }
            i = end + 1
        }
        return unescapeEntities(out.toString()).trim()
    }

    // ------------------------------------------------------------------ 标签扫描

    /** `<` 的解读：开始/结束标签、声明或注释、非标签（普通文本 `<`）。 */
    private enum class TagKind { START, END, DECLARATION, NOT_TAG }

    /**
     * 判定 [at] 处的 `<` 是否开始一个标签：`</`（结束）、`<!`/`<?`（声明/注释）、
     * ASCII 字母（开始标签，与旧正则 `[a-zA-Z]` 口径一致）视为标签，其余为普通文本。
     */
    private fun tagKindAt(html: String, at: Int): TagKind {
        val next = at + 1
        if (next >= html.length) return TagKind.NOT_TAG
        return when (val c = html[next]) {
            '/' -> TagKind.END
            '!', '?' -> TagKind.DECLARATION
            else -> if (c in 'a'..'z' || c in 'A'..'Z') TagKind.START else TagKind.NOT_TAG
        }
    }

    /**
     * 引号感知地找标签结束 `>`：标签内进入 `"..."` / `'...'` 引用态后，引用态里的
     * `>` 不算标签结束（如 `title="a>b"`），引用由**同种**引号闭合。
     *
     * @return 闭合 `>` 的下标；找不到（到串尾仍未闭合）返回 -1。
     */
    private fun findTagEnd(html: String, tagStart: Int): Int {
        var quote: Char? = null
        var i = tagStart + 1
        while (i < html.length) {
            val c = html[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return i
            }
            i++
        }
        return -1
    }

    // ------------------------------------------------------------------ href/src 属性提取

    /** 可提取 URL 的属性名（大小写不敏感），与旧正则 `(?:href|src)` 口径一致。 */
    private val URL_ATTRIBUTE_NAMES = arrayOf("href", "src")

    /**
     * 在开始标签文本（含 `<` 与 `>`）里查找首个 `href`/`src` 属性并返回其**带引号的**
     * 属性值；属性名要求边界正确（前邻空白/引号/`/`，故 `xhref`、`data-href` 不算），
     * 属性名后允许空白、`=`、空白（故 `hreflang` 这类更长属性名不算），值须为 `"..."` /
     * `'...'` 且非空——**不带引号的属性值按旧正则口径不提取**（整个标签按普通标签丢弃）。
     *
     * @return URL 属性值；无命中返回 null。
     */
    private fun findUrlAttribute(tag: String): String? {
        var i = 1 // 跳过 `<`；位置 0 的 `<` 不可能是属性名边界前的内容
        while (i < tag.length) {
            for (name in URL_ATTRIBUTE_NAMES) {
                val nameEnd = i + name.length
                if (nameEnd >= tag.length) continue
                if (!tag.regionMatches(i, name, 0, name.length, ignoreCase = true)) continue
                if (!isAttributeNameBoundary(tag[i - 1])) continue
                val value = readQuotedAttributeValue(tag, nameEnd) ?: continue
                if (value.isNotEmpty()) return value
            }
            i++
        }
        return null
    }

    /** 属性名左边界：空白、属性值闭合引号或自闭合 `/`（字母/数字/`-`/`_` 等都不是）。 */
    private fun isAttributeNameBoundary(c: Char): Boolean =
        c.isWhitespace() || c == '"' || c == '\'' || c == '/'

    /**
     * 从属性名之后读取「空白* `=` 空白* 引号值」；不符合该形态（如 `hreflang`、
     * `href = 裸值`）返回 null。
     */
    private fun readQuotedAttributeValue(tag: String, afterName: Int): String? {
        var i = afterName
        while (i < tag.length && tag[i].isWhitespace()) i++
        if (i >= tag.length || tag[i] != '=') return null
        i++
        while (i < tag.length && tag[i].isWhitespace()) i++
        if (i >= tag.length || (tag[i] != '"' && tag[i] != '\'')) return null
        val quote = tag[i]
        i++
        val valueStart = i
        while (i < tag.length && tag[i] != quote) i++
        if (i >= tag.length) return null // 引用未闭合：不提取（标签本身已由 findTagEnd 收口）
        return tag.substring(valueStart, i)
    }

    // ------------------------------------------------------------------ 实体反转义

    /** 常见实体反转义；`&amp;` 必须最后替换，防止二次反转义。 */
    private fun unescapeEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
