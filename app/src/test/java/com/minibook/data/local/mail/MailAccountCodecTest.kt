package com.minibook.data.local.mail

import com.minibook.domain.model.MailAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MailAccountCodec] 纯 JVM 单测（F-13 多账号增强，**手写解析实现**的边界覆盖）。
 *
 * 覆盖：encode/decode 往返（含字符串转义、`\uXXXX` 控制字符、值内结构字符）；空/非法
 * JSON 返回空列表（不抛）；字符串未闭合；键序颠倒丢弃；条目缺 id/邮箱丢弃；垃圾文本
 * 中挑出合法条目；id 去重保留首见；token 间多行空白；未知转义原样保留；邮箱格式与
 * 16 位授权码校验边界。
 * 本测试不使用任何真实邮箱地址/授权码（邮箱一律 `@example.com` 保留域）。
 */
class MailAccountCodecTest {

    // ---------- encode / decode 往返 ----------

    @Test
    fun encodeDecode_roundtrip_multipleAccounts() {
        val accounts = listOf(
            MailAccount(id = "id-1", email = "a1@example.com"),
            MailAccount(id = "id-2", email = "a2@example.com"),
            MailAccount(id = "id-3", email = "a3@example.com"),
        )
        val decoded = MailAccountCodec.decode(MailAccountCodec.encode(accounts))
        assertEquals(accounts, decoded)
    }

    @Test
    fun encode_emptyList_isEmptyJsonArray() {
        assertEquals("[]", MailAccountCodec.encode(emptyList()))
        assertTrue(MailAccountCodec.decode("[]").isEmpty())
    }

    @Test
    fun encodeDecode_roundtrip_escapedCharacters() {
        // id/email 含引号、反斜杠与控制字符：编码必须转义且往返无损。
        // （控制字符放在值内部——decode 对首尾空白做 trim 容错，属既定行为。）
        val accounts = listOf(
            MailAccount(id = "id\"x\\y", email = "na\"me\\@example.com"),
            MailAccount(id = "id\ttab", email = "line\nbreak@example.com"),
        )
        val decoded = MailAccountCodec.decode(MailAccountCodec.encode(accounts))
        assertEquals(accounts, decoded)
    }

    // ---------- decode 容错 ----------

    @Test
    fun decode_nullOrBlank_returnsEmptyList() {
        assertTrue(MailAccountCodec.decode(null).isEmpty())
        assertTrue(MailAccountCodec.decode("").isEmpty())
        assertTrue(MailAccountCodec.decode("   ").isEmpty())
    }

    @Test
    fun decode_invalidJson_returnsEmptyList() {
        assertTrue(MailAccountCodec.decode("not a json").isEmpty())
        assertTrue(MailAccountCodec.decode("{\"id\":123,\"email\":456}").isEmpty())
        assertTrue(MailAccountCodec.decode("[{\"id\":\"a\"}").isEmpty())
        // 纯垃圾/花括号文本（条目骨架不符）：空列表，不抛。
        assertTrue(MailAccountCodec.decode("{{{}}}").isEmpty())
        assertTrue(MailAccountCodec.decode("[{\"id\":\"a\",\"email\":\"b@example.com\"").isEmpty())
    }

    @Test
    fun decode_unterminatedString_dropped_notThrows() {
        // 字符串未闭合（找不到不带转义的闭引号）：条目丢弃，解析器不抛、不卡死。
        assertTrue(MailAccountCodec.decode("""[{"id":"abc,"email":"a@example.com"}]""").isEmpty())
        assertTrue(MailAccountCodec.decode("""[{"id":"abc\","email":"a@example.com"}]""").isEmpty())
    }

    @Test
    fun decode_keyOrderSwapped_dropped() {
        // 键序固定 id 在前（与旧正则口径一致）：email 在前的条目整体丢弃。
        assertTrue(MailAccountCodec.decode("""[{"email":"a@example.com","id":"x"}]""").isEmpty())
    }

    @Test
    fun decode_entryMissingIdOrEmail_dropped() {
        val json = """[
            {"id":"","email":"a@example.com"},
            {"id":"id-2","email":"   "},
            {"id":"id-3","email":"ok@example.com"}
        ]"""
        val decoded = MailAccountCodec.decode(json)
        assertEquals(listOf(MailAccount("id-3", "ok@example.com")), decoded)
    }

    @Test
    fun decode_partialEntriesAmongGarbage_keepsValidOne() {
        // 合法条目与残缺条目/垃圾文本混排：只解出合法那条（与旧正则 findAll 口径一致）。
        val json = """prefix[{"id":"bad"},{"id":"good","email":"ok@example.com"},]suffix"""
        assertEquals(listOf(MailAccount("good", "ok@example.com")), MailAccountCodec.decode(json))
    }

    @Test
    fun decode_duplicateIds_keepsFirstSeen() {
        val json = """[
            {"id":"dup","email":"first@example.com"},
            {"id":"dup","email":"second@example.com"},
            {"id":"other","email":"other@example.com"}
        ]"""
        val decoded = MailAccountCodec.decode(json)
        assertEquals(
            listOf(
                MailAccount("dup", "first@example.com"),
                MailAccount("other", "other@example.com"),
            ),
            decoded,
        )
    }

    @Test
    fun decode_trimsWhitespaceAroundValues() {
        val json = """[{"id":" id-1 ","email":" a@example.com "}]"""
        val decoded = MailAccountCodec.decode(json)
        assertEquals(listOf(MailAccount("id-1", "a@example.com")), decoded)
    }

    @Test
    fun decode_multiLineJson_withWhitespaceBetweenTokens() {
        // 多行 + token 间任意空白（键名内部除外）的手写解析边界。
        val json = """
            [
              { "id" : "id-1" , "email" : "a@example.com" } ,
              {"id":"id-2","email":"b@example.com"}
            ]
        """.trimIndent()
        assertEquals(
            listOf(MailAccount("id-1", "a@example.com"), MailAccount("id-2", "b@example.com")),
            MailAccountCodec.decode(json),
        )
    }

    @Test
    fun decode_valuesContainingBracesAndBrackets_notConfused() {
        // 值内含 `{` `}` `[` `]` `,` `:` 等结构字符：字符串扫描不受其干扰。
        val json = """[{"id":"i{d}[1],:","email":"a{b}[c],:@example.com"}]"""
        assertEquals(
            listOf(MailAccount("i{d}[1],:", "a{b}[c],:@example.com")),
            MailAccountCodec.decode(json),
        )
    }

    @Test
    fun decode_unicodeEscapedControlChars_roundtrip() {
        // encode 对 < 0x20 控制字符统一 \uXXXX 转义：须能解回。
        val accounts = listOf(MailAccount(id = "a\u0001b", email = "x\u0001y@example.com"))
        val encoded = MailAccountCodec.encode(accounts)
        assertEquals(accounts, MailAccountCodec.decode(encoded))
        // 手写含 \uXXXX 的 JSON 同样反转义正确。
        val json = """[{"id":"a\u0001b","email":"xy@example.com"}]"""
        assertEquals(
            listOf(MailAccount("a\u0001b", "xy@example.com")),
            MailAccountCodec.decode(json),
        )
    }

    @Test
    fun decode_unknownEscapeSequence_keptLiterally() {
        // 非 [escapeJson] 产出的转义（如 \q）：反斜杠原样保留（与旧 unescapeJson 口径一致）。
        val json = """[{"id":"a\qb","email":"c@example.com"}]"""
        assertEquals(listOf(MailAccount("""a\qb""", "c@example.com")), MailAccountCodec.decode(json))
    }

    // ---------- validateEmail 边界 ----------

    @Test
    fun validateEmail_boundaries() {
        assertTrue(MailAccountCodec.validateEmail("name@example.com"))
        assertTrue(MailAccountCodec.validateEmail("a@b.c"))
        assertTrue(MailAccountCodec.validateEmail("user.name@sub.example.com"))
        assertTrue(MailAccountCodec.validateEmail("name@a.b.c")) // 多段域名（点在中间即合法）
        assertFalse(MailAccountCodec.validateEmail("name@example")) // 域名缺 `.`
        assertFalse(MailAccountCodec.validateEmail("name@.com")) // 点前无字符
        assertFalse(MailAccountCodec.validateEmail("name@example.")) // 点后无字符（尾点）
        assertFalse(MailAccountCodec.validateEmail("@example.com")) // 缺本地名
        assertFalse(MailAccountCodec.validateEmail("name@@example.com")) // 双 @
        assertFalse(MailAccountCodec.validateEmail("na me@example.com")) // 本地名含空白
        assertFalse(MailAccountCodec.validateEmail("name@example .com")) // 域名含空白
        assertFalse(MailAccountCodec.validateEmail("name@example\t.com")) // 域名含制表符
        assertFalse(MailAccountCodec.validateEmail("")) // 空串
        assertFalse(MailAccountCodec.validateEmail("name@")) // 空域名
    }

    // ---------- validateAuthCode 边界 ----------

    @Test
    fun validateAuthCode_boundaries() {
        assertTrue(MailAccountCodec.validateAuthCode("0123456789abcdef")) // 恰 16 位
        assertTrue(MailAccountCodec.validateAuthCode(" 0123456789abcdef ")) // trim 后 16 位
        assertFalse(MailAccountCodec.validateAuthCode("0123456789abcde")) // 15 位
        assertFalse(MailAccountCodec.validateAuthCode("0123456789abcdef1")) // 17 位
        assertFalse(MailAccountCodec.validateAuthCode("")) // 空串
        assertFalse(MailAccountCodec.validateAuthCode("0123 456789abcd")) // trim 后 15 位（内空白不补位）
    }
}
