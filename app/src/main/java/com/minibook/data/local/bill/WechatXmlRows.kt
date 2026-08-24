package com.minibook.data.local.bill

/**
 * 微信 XLSX 内部 XML 的纯字符串解码器（F-12，DEC-016 零 xlsx 库）。
 *
 * 不依赖 `org.xmlpull.v1.XmlPullParser`（Android 框架类，JVM 单测测试源集未必提供），
 * 以「正则 + 字符串」解析 OOXML 的两个窄结构：`xl/sharedStrings.xml`（共享字符串表）与
 * `xl/worksheets/sheet1.xml`（主工作表）。输入是**明文字符串**（由 [WechatXlsxParser]
 * 从 `ZipFile` 读出），因此 JVM 单测可直接喂 XML 字符串断言（DEC-016 #7 测纯函数）。
 *
 * 结构口径（OOXML sheet 单元格）：
 * - `<row r="1" ...><c r="A1" t="s|str|inlineStr|b|n"> ... </c>...</row>`；
 * - `t="s"`：`<v>N</v>` 为 sharedStrings 下标；
 * - `t="inlineStr"`：值在 `<is><t>..</t></is>`（富文本 `<is><r><t>..</t></r></is>` 拼接）；
 * - 其余（`t` 缺失/`str`/`b`/`n`）：`<v>` 取字面量（日期序列数/金额等，不 parse 为 double）；
 * - cell 可能稀疏（空 cell 缺 `<c>`），按 `r` 字母前缀算**绝对列序**（DEC-016 #6）。
 */
object WechatXmlRows {

    // ---- 标签匹配：`(?=[\\s>])` 前瞻排除 `<text>`/`<col>` 等前缀误捕（只匹配完整标签名） ----

    private val SI_REGEX = Regex("<si(?=[\\s>])[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val T_REGEX = Regex("<t(?=[\\s>])[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val ROW_REGEX = Regex("<row(?=[\\s>])[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    private val CELL_REGEX = Regex("<c(?=[\\s>])([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
    private val V_REGEX = Regex("<v(?=[\\s>])[^>]*>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    private val IS_REGEX = Regex("<is(?=[\\s>])[^>]*>(.*?)</is>", RegexOption.DOT_MATCHES_ALL)
    private val R_ATTR_REGEX = Regex("""\sr="([^"]*)"""")
    private val T_ATTR_REGEX = Regex("""\st="([^"]*)"""")

    /** 自闭合 `<row .../>` / `<c .../>` 归一化为成对标签，避免与相邻标签被非贪婪匹配误并。 */
    private val SELF_CLOSING_REGEX = Regex("<(row|c)\\b([^>]*?)/>")

    /**
     * 解析 `sharedStrings.xml` → 按出现顺序的共享字符串列表（富文本 `<r><t>` 拼接）。
     *
     * @param xml sharedStrings.xml 原文（明文字符串）；空/无 `<si>` 返回空列表。
     */
    fun decodeSharedStrings(xml: String): List<String> {
        val result = mutableListOf<String>()
        SI_REGEX.findAll(xml).forEach { si ->
            result.add(concatTextRuns(si.groupValues[1]))
        }
        return result
    }

    /**
     * 解码 `sheet1.xml` → 行列表；每行是「绝对列序 → 单元格文本值」的稀疏映射
     * （`t=s` 反查 [sharedStrings]，`inlineStr` 取 `<is><t>`，其余 `<v>` 字面量）。
     *
     * @param sharedStrings 由 [decodeSharedStrings] 产出的共享字符串表。
     */
    fun decodeSheet(sheetXml: String, sharedStrings: List<String>): List<Map<Int, String>> {
        val normalized = SELF_CLOSING_REGEX.replace(sheetXml) { m ->
            "<${m.groupValues[1]}${m.groupValues[2]}></${m.groupValues[1]}>"
        }
        val rows = mutableListOf<Map<Int, String>>()
        ROW_REGEX.findAll(normalized).forEach { rowMatch ->
            val row = linkedMapOf<Int, String>()
            CELL_REGEX.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                val attrs = cellMatch.groupValues[1]
                val inner = cellMatch.groupValues[2]
                val ref = attrValue(attrs, R_ATTR_REGEX) ?: return@forEach
                val columnIndex = columnIndexOf(ref) ?: return@forEach
                row[columnIndex] = resolveCellValue(attrs, inner, sharedStrings)
            }
            rows.add(row)
        }
        return rows
    }

    /** 单元格值解析：`t=s` 反查共享串；`inlineStr` 取 `<is><t>`；其余 `<v>` 字面量。 */
    private fun resolveCellValue(attrs: String, inner: String, sharedStrings: List<String>): String =
        when (attrValue(attrs, T_ATTR_REGEX)) {
            "s" -> {
                val index = V_REGEX.find(inner)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                if (index != null && index in sharedStrings.indices) sharedStrings[index] else ""
            }
            "inlineStr" -> {
                val isInner = IS_REGEX.find(inner)?.groupValues?.get(1)
                if (isInner != null) concatTextRuns(isInner) else ""
            }
            else -> V_REGEX.find(inner)?.groupValues?.get(1).orEmpty()
        }

    /** 拼接块内全部 `<t>`（普通 + 富文本 run），并解 XML 实体转义。 */
    private fun concatTextRuns(block: String): String {
        val sb = StringBuilder()
        T_REGEX.findAll(block).forEach { t -> sb.append(unescapeXml(t.groupValues[1])) }
        return sb.toString()
    }

    /** 取标签属性值（` r="A1"` → `A1`）；无该属性返回 null。 */
    private fun attrValue(tag: String, regex: Regex): String? =
        regex.find(tag)?.groupValues?.get(1)

    /**
     * 列字母（`r` 前缀）→ 0 起绝对列序：`A`→0、`B`→1、`AA`→26。非字母前缀返回 null。
     */
    private fun columnIndexOf(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return null
        var n = 0
        for (ch in letters) {
            n = n * 26 + (ch - 'A' + 1)
        }
        return n - 1
    }

    /** 解基础 XML 实体（具名 + 十进制/十六进制字符引用）；`&amp;` 最后解避免双重转义。 */
    private fun unescapeXml(s: String): String {
        var r = s
        r = r.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        r = Regex("&#(\\d+);").replace(r) { m -> m.groupValues[1].toInt().toChar().toString() }
        r = r.replace("&amp;", "&")
        return r
    }
}