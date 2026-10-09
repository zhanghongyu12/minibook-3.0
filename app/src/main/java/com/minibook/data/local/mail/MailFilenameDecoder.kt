package com.minibook.data.local.mail

import java.nio.charset.Charset
import java.util.Base64
import javax.inject.Inject

/**
 * 邮件附件文件名解码器（F-13，纯 Kotlin，JVM 可单测）。
 *
 * JavaMail 的 `getFileName()` 在 `mail.mime.decodefilename=false`（默认）时返回
 * **原始未解码**串，QQ 邮箱实测样本涉及三类形态：
 *
 * 1. **RFC 2231 续行**：`name*0` / `name*1` 多段参数，按序号升序**首尾直接相接**
 *    （不插任何分隔符）后再解码——QQ 邮箱的折行甚至会把一个 encoded-word 从中间
 *    劈开，拼接后才还原为合法的 RFC 2047 串；
 * 2. **RFC 2047 encoded-word**：`=?charset?Q?...?=`（Q 编码：`=XX` 是十六进制
 *    **字节**、须按 charset（实测为 GB18030）解码，`_` 表示空格）与
 *    `=?charset?B?...?=`（Base64）；**相邻两个 encoded-word 之间仅隔空白时，
 *    空白必须丢弃**（RFC 2047 §6.2），两段解码结果直接连接；
 * 3. **折行残留与非法写法**：值里可能残留 CRLF + 空格折行（先去 `\r`/`\n`），
 *    以及非法但真实存在的 `filename=GB18030?Q?...?=`（少一处 `=`，encoded-word
 *    的开头「=?」被 `filename=` 吃掉）需容错。
 *
 * 任何解码失败（非法 Base64 / 未知字符集 / 非法十六进制）都**原样返回输入**，
 * 绝不抛异常——附件名只是展示与命名参考，不应让解码失败中断取件流程。
 */
class MailFilenameDecoder @Inject constructor() {

    /** 解码邮件附件文件名；null 入参返回 null；解码失败原样返回输入（不抛异常）。 */
    fun decode(raw: String?): String? {
        if (raw == null) return null
        return try {
            decodeInternal(raw)
        } catch (e: Exception) {
            // 失败兜底：原样返回，调用方拿到的是可用性最接近的原始串。
            raw
        }
    }

    /**
     * 拼接 RFC 2231 续行参数（如 `name*0` / `name*1` 的值），按序号升序直接相接
     * （不插任何分隔符）——QQ 邮箱的续行本质是「一个文件名被硬折行劈开」，
     * 中间不能有分隔符。
     */
    fun joinRfc2231Parts(partsByIndex: Map<Int, String>): String =
        partsByIndex.toSortedMap().values.joinToString("")

    // ------------------------------------------------------------------ 内部实现

    private fun decodeInternal(raw: String): String {
        val unfolded = raw.replace("\r", "").replace("\n", "")
        val value = normalize(unfolded)
        val matches = ENCODED_WORD_REGEX.findAll(value).toList()
        if (matches.isEmpty()) {
            // 未编码的普通文件名：原样返回（仅当确实含折行时返回去折行结果）。
            return if (unfolded == raw) raw else unfolded
        }
        val builder = StringBuilder()
        var lastEnd = 0
        var previousWasEncodedWord = false
        for (match in matches) {
            val gap = value.substring(lastEnd, match.range.first)
            // RFC 2047：相邻 encoded-word 之间仅空白 → 丢弃；夹杂其他文本 → 原样保留。
            if (!(previousWasEncodedWord && gap.isBlank())) builder.append(gap)
            builder.append(
                decodeEncodedWord(
                    charsetToken = match.groupValues[1],
                    encoding = match.groupValues[2],
                    text = match.groupValues[3],
                ),
            )
            lastEnd = match.range.last + 1
            previousWasEncodedWord = true
        }
        builder.append(value.substring(lastEnd))
        return builder.toString()
    }

    /** 统一入参形态：去外层引号、剥残留的 `name=`/`filename=` 前缀、容错「少一处 =」的非法写法。 */
    private fun normalize(value: String): String {
        var v = value.trim()
        if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length - 1)
        }
        NAME_PREFIX_REGEX.find(v)?.let { v = v.substring(it.value.length).trim() }
        // 非法但真实存在：filename=GB18030?Q?...?=（encoded-word 的开头「=?」缺一处
        // 「=」被 filename= 占用）——补回「=?」再走正规解码。
        if (MISSING_EQUALS_PREFIX_REGEX.matches(v)) v = "=?$v"
        return v
    }

    private fun decodeEncodedWord(charsetToken: String, encoding: String, text: String): String {
        val charset = Charset.forName(charsetToken)
        return when (encoding.uppercase()) {
            "Q" -> String(decodeQ(text), charset)
            "B" -> String(Base64.getDecoder().decode(text), charset)
            else -> throw IllegalArgumentException("未知 encoded-word 编码: $encoding")
        }
    }

    /** RFC 2047 Q 编码：`_` = 空格，`=XX` = 一个十六进制字节；非法序列抛异常（整体回退原样返回）。 */
    private fun decodeQ(text: String): ByteArray {
        val bytes = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                c == '_' -> {
                    bytes.add(' '.code.toByte())
                    index += 1
                }
                c == '=' -> {
                    if (index + 3 > text.length) throw IllegalArgumentException("Q 编码十六进制不完整")
                    bytes.add(text.substring(index + 1, index + 3).toInt(16).toByte())
                    index += 3
                }
                c.code <= 0xFF -> {
                    bytes.add(c.code.toByte())
                    index += 1
                }
                else -> throw IllegalArgumentException("Q 编码含非 Latin-1 字符")
            }
        }
        return bytes.toByteArray()
    }

    private companion object {
        /** RFC 2047 encoded-word：`=?charset?Q/B?text?=`（text 内不允许出现 `?`）。 */
        val ENCODED_WORD_REGEX = Regex("""=[?]([^?]+)[?]([bBqQ])[?]([^?]*)[?]=""")

        /** 残留的 `name=` / `filename=` 前缀（大小写不敏感），见于原始头片段形态。 */
        val NAME_PREFIX_REGEX = Regex("""(?i)^(?:file)?name\s*=""")

        /** 「少一处 =」的非法 encoded-word 整体形态：`charset?Q?...?=`（缺开头 `=?`）。 */
        val MISSING_EQUALS_PREFIX_REGEX = Regex("""^[A-Za-z0-9.!*_-]+[?][bBqQ][?].*[?]=?$""")
    }
}
