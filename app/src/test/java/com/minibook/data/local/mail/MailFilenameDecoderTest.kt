package com.minibook.data.local.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 附件文件名解码器单测（F-13，纯 JVM，不发网络请求）。
 *
 * 覆盖：两组 QQ 邮箱**真实实测样本**（RFC2231 续行拼接 + RFC2047 Q 编码 GB18030）、
 * B 编码（Base64）、相邻 encoded-word 空白丢弃（含 CRLF 折行形态）、值内折行、
 * `_` 表示空格、null 入参、未编码普通文件名原样返回、解码失败原样返回、
 * `filename=` 前缀残留与「少一处 =」非法写法容错、外层引号剥除。
 */
class MailFilenameDecoderTest {

    private val decoder = MailFilenameDecoder()

    /** 真实样本一（QQ 邮箱支付宝账单附件）：期望 支付宝交易明细(20260619-20260919).zip */
    private val sample1Part0 = "=?GB18030?Q?=D6=A7=B8=B6=B1=A6=BD=BB=D2=D7=C3=F7=CF=B8(20260"
    private val sample1Part1 = "61?= =?GB18030?Q?9-20260919).zip?="

    /** 真实样本二（QQ 邮箱支付宝账单附件）：期望 支付宝交易明细(20260501-20260824).zip */
    private val sample2Part0 = "=?GB18030?Q?=D6=A7=B8=B6=B1=A6=BD=BB=D2=D7=C3=F7=CF=B8(20260"
    private val sample2Part1 = "50?= =?GB18030?Q?1-20260824).zip?="

    @Test
    fun `joinRfc2231Parts按序号升序首尾直接相接`() {
        assertEquals(
            sample1Part0 + sample1Part1,
            decoder.joinRfc2231Parts(mapOf(1 to sample1Part1, 0 to sample1Part0)),
        )
    }

    @Test
    fun `真实样本一拼接后解码为支付宝账单文件名`() {
        val joined = decoder.joinRfc2231Parts(mapOf(0 to sample1Part0, 1 to sample1Part1))
        assertEquals("支付宝交易明细(20260619-20260919).zip", decoder.decode(joined))
    }

    @Test
    fun `真实样本二拼接后解码为支付宝账单文件名`() {
        val joined = decoder.joinRfc2231Parts(mapOf(0 to sample2Part0, 1 to sample2Part1))
        assertEquals("支付宝交易明细(20260501-20260824).zip", decoder.decode(joined))
    }

    @Test
    fun `getFileName形态的完整串直接解码`() {
        // JavaMail getFileName() 可能直接给到拼接后的完整串，无需 joinRfc2231Parts。
        assertEquals(
            "支付宝交易明细(20260619-20260919).zip",
            decoder.decode(sample1Part0 + sample1Part1),
        )
    }

    @Test
    fun `B编码Base64按字符集解码`() {
        // “支付宝” 的 GB18030 字节 D6 A7 B8 B6 B1 A6 的 Base64 = 1qe4trGm。
        assertEquals("支付宝", decoder.decode("=?GB18030?B?1qe4trGm?="))
    }

    @Test
    fun `Q编码下划线表示空格`() {
        assertEquals("a b", decoder.decode("=?UTF-8?Q?a_b?="))
    }

    @Test
    fun `相邻encoded-word之间空白丢弃`() {
        assertEquals("ab", decoder.decode("=?UTF-8?Q?a?= =?UTF-8?Q?b?="))
        assertEquals("ab", decoder.decode("=?UTF-8?Q?a?=   =?UTF-8?Q?b?="))
    }

    @Test
    fun `相邻encoded-word之间夹其他文本时文本保留`() {
        assertEquals("a - b", decoder.decode("=?UTF-8?Q?a?= - =?UTF-8?Q?b?="))
    }

    @Test
    fun `相邻encoded-word之间为CRLF折行空白同样丢弃`() {
        assertEquals("测试", decoder.decode("=?UTF-8?Q?=E6=B5=8B?=\r\n =?UTF-8?Q?=E8=AF=95?="))
    }

    @Test
    fun `值内CRLF折行先去除`() {
        assertEquals("a b", decoder.decode("=?UTF-8?Q?a\r\n b?="))
    }

    @Test
    fun `null入参返回null`() {
        assertNull(decoder.decode(null))
    }

    @Test
    fun `未编码普通文件名原样返回`() {
        assertEquals("bill_20260919.zip", decoder.decode("bill_20260919.zip"))
        assertEquals("微信支付账单.zip", decoder.decode("微信支付账单.zip"))
    }

    @Test
    fun `解码失败原样返回输入`() {
        // 非法 Base64
        assertEquals("=?UTF-8?B?***?=", decoder.decode("=?UTF-8?B?***?="))
        // 未知字符集
        assertEquals("=?X-NoSuchCharset?Q?abc?=", decoder.decode("=?X-NoSuchCharset?Q?abc?="))
        // 非法十六进制
        assertEquals("=?UTF-8?Q?=ZZ?=", decoder.decode("=?UTF-8?Q?=ZZ?="))
    }

    @Test
    fun `容错残留filename前缀`() {
        assertEquals("支", decoder.decode("filename==?GB18030?Q?=D6=A7?="))
    }

    @Test
    fun `容错少一处等号的非法filename写法`() {
        // 非法但真实存在：filename=GB18030?Q?...?=（encoded-word 开头的「=?」缺一处「=」）。
        assertEquals("支", decoder.decode("filename=GB18030?Q?=D6=A7?="))
    }

    @Test
    fun `外层双引号被剥掉`() {
        assertEquals("支", decoder.decode("\"=?GB18030?Q?=D6=A7?=\""))
    }
}
