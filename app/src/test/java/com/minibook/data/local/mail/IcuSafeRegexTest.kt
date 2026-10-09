package com.minibook.data.local.mail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「ICU 绊线」测试：守护 F-13 邮件相关源码中 `Regex(...)` 字面量的 ICU 安全性。
 *
 * **为什么需要这个测试**：Android 的 `java.util.regex` 底层是 ICU，与 JDK 正则行为
 * 不一致——对普通标点的反斜杠转义（如 `\}`）JDK 接受、ICU 抛 `PatternSyntaxException`。
 * 历史上 [MailAccountCodec] 的 `ENTRY_REGEX` 因 `\}` 在真机 `<clinit>` 抛
 * `PatternSyntaxException`（被包成 `ExceptionInInitializerError`），导致 JVM 单测全绿、
 * 真机账号迁移半途丢失凭据。此测试在 JVM 侧扫描源码文本，绊住这类「JVM 测不出」的
 * 高危写法再次混入。
 *
 * 检查口径：受审计源文件中每个 `Regex(...)` 调用的**字符串字面量**内，不得出现
 * 反斜杠紧跟 `{ } [ ] ? * -` 这类普通标点的转义（`\s` `\d` `\w` `\.` `\b` `\uXXXX`
 * `\\` 等标准转义不受限）。`WechatXmlRows.kt` 为既有已真机验证代码，刻意不审计。
 *
 * 实现取舍：用轻量的字面量扫描器（识别 `Regex(` 后的 `"""..."""` 原始串或 `"..."`
 * 普通串）而非完整 Kotlin 语法解析——对本项目这几个文件的写法足够稳健；若某处
 * `Regex(` 后跟的不是字符串字面量（如注释里的 `Regex(`），该处跳过不误报。
 */
class IcuSafeRegexTest {

    @Test
    fun `受审计源文件的Regex字面量不含ICU高危转义`() {
        val violations = mutableListOf<String>()
        for (relativePath in AUDITED_FILES) {
            val source = resolveSourceFile(relativePath).readText()
            for (literal in extractRegexStringLiterals(source)) {
                for (escape in forbiddenEscapes(literal)) {
                    violations.add("$relativePath: Regex 字面量含 ICU 高危转义 \"$escape\"")
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `MailAccountCodec完全不得使用正则`() {
        // 根因修复纪律：该类历史上因正则转义在真机 <clinit> 崩溃，现须保持零正则。
        val source = resolveSourceFile("data/local/mail/MailAccountCodec.kt").readText()
        assertTrue("MailAccountCodec.kt 不得出现 Regex(", !source.contains("Regex("))
        assertTrue("MailAccountCodec.kt 不得出现 toRegex(", !source.contains("toRegex("))
    }

    // ------------------------------------------------------------------ 内部实现

    private companion object {
        /** 受审计的 F-13 邮件源文件（相对 `com/minibook` 包根；路径分隔符统一用 `/`）。 */
        private val AUDITED_FILES = listOf(
            "data/local/mail/MailAccountCodec.kt",
            "data/local/mail/WechatBillLinkExtractor.kt",
            "data/local/mail/MailHtmlText.kt",
            "data/local/mail/MailBillDownloader.kt",
            "data/local/mail/MailFilenameDecoder.kt",
        )

        /** ICU 高危转义：反斜杠 + 普通标点（JDK 宽容、ICU 可能抛 PatternSyntaxException）。 */
        private const val FORBIDDEN_AFTER_BACKSLASH = "{}[]?*-"

        /** 定位源码根：Gradle JVM 单测工作目录通常是模块根 `app/`，也兼容仓库根。 */
        private fun sourceRoot(): File {
            var dir: File = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(6) {
                // 工作目录 = 模块根 app/
                File(dir, "src/main/java/com/minibook/data/local/mail/MailAccountCodec.kt")
                    .takeIf { it.isFile }
                    ?.let { return File(dir, "src/main/java/com/minibook") }
                // 工作目录 = 仓库根
                File(dir, "app/src/main/java/com/minibook/data/local/mail/MailAccountCodec.kt")
                    .takeIf { it.isFile }
                    ?.let { return File(dir, "app/src/main/java/com/minibook") }
                dir = dir.parentFile ?: return@repeat
            }
            error("无法定位受审计源码目录（user.dir=${System.getProperty("user.dir")}）")
        }

        /** 解析受审计源文件；找不到直接失败（绊线宁可报错也不静默跳过）。 */
        private fun resolveSourceFile(relativePath: String): File {
            val file = File(sourceRoot(), relativePath)
            assertTrue("找不到受审计源文件: ${file.absolutePath}", file.isFile)
            return file
        }

        /**
         * 提取源码中所有 `Regex(` 调用的字符串字面量内容（支持 `Regex("""...""")`、
         * `Regex("...")`、`Regex(pattern = ...)`）。`Regex(` 后不是字符串起始则跳过。
         */
        private fun extractRegexStringLiterals(source: String): List<String> {
            val literals = mutableListOf<String>()
            var callIndex = source.indexOf("Regex(")
            while (callIndex >= 0) {
                var i = callIndex + "Regex(".length
                i = skipIrrelevant(source, i)
                if (source.startsWith("\"\"\"", i)) {
                    readRawString(source, i + 3)?.let(literals::add)
                } else if (i < source.length && source[i] == '"') {
                    readEscapedString(source, i + 1)?.let(literals::add)
                }
                callIndex = source.indexOf("Regex(", callIndex + 1)
            }
            return literals
        }

        /** 跳过空白与 `pattern = ` 命名参数前缀。 */
        private fun skipIrrelevant(source: String, start: Int): Int {
            var i = start
            while (i < source.length && source[i].isWhitespace()) i++
            if (source.startsWith("pattern", i)) {
                i += "pattern".length
                while (i < source.length && source[i].isWhitespace()) i++
                if (i < source.length && source[i] == '=') i++
                while (i < source.length && source[i].isWhitespace()) i++
            }
            return i
        }

        /** 读 `"""..."""` 原始串（从开三引号之后的位置起）；返回内容或 null（未闭合）。 */
        private fun readRawString(source: String, start: Int): String? {
            val end = source.indexOf("\"\"\"", start)
            return if (end < 0) null else source.substring(start, end)
        }

        /** 读 `"..."` 普通串（从开引号之后的位置起，处理 Kotlin `\\` 转义）；未闭合返回 null。 */
        private fun readEscapedString(source: String, start: Int): String? {
            val builder = StringBuilder()
            var i = start
            while (i < source.length) {
                val c = source[i]
                when {
                    c == '"' -> return builder.toString()
                    c == '\\' -> {
                        if (i + 1 >= source.length) return null
                        builder.append(source[i + 1]) // 转义还原后即字面量实际内容
                        i += 2
                    }
                    else -> {
                        builder.append(c)
                        i++
                    }
                }
            }
            return null
        }

        /** 找出字面量中所有「反斜杠 + 高危标点」序列。 */
        private fun forbiddenEscapes(literal: String): List<String> {
            val hits = mutableListOf<String>()
            var i = 0
            while (i + 1 < literal.length) {
                if (literal[i] == '\\') {
                    val next = literal[i + 1]
                    if (next == '\\') {
                        i += 2 // 正则的转义反斜杠：安全，成对跳过
                        continue
                    }
                    if (FORBIDDEN_AFTER_BACKSLASH.indexOf(next) >= 0) hits.add("\\" + next)
                    i += 2
                } else {
                    i++
                }
            }
            return hits
        }
    }
}
