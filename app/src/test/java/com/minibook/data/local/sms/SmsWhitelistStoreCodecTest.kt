package com.minibook.data.local.sms

import com.minibook.domain.model.SmsWhitelistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-14 白名单序列化单测（设计 §3.8）。
 *
 * 只测纯编解码（不依赖 Android DataStore）：DataStore 交互属 androidTest（真机/仪器测试，
 * 待用户批准后执行）。
 */
class SmsWhitelistStoreCodecTest {

    private val codec = SmsWhitelistStoreCodec

    @Test
    fun `编解码往返：字段无损`() {
        val entry = SmsWhitelistEntry(
            sender = "95526",
            autoEnabled = true,
            confirmedCount = 3,
            lastErrorAtMs = 1_791_518_400_000L,
        )
        val decoded = codec.decode(setOf(codec.encode(entry)))
        assertEquals(1, decoded.size)
        assertEquals(entry, decoded.first())
    }

    @Test
    fun `编解码往返：无错误时间戳`() {
        val entry = SmsWhitelistEntry(sender = "10690000", autoEnabled = false, confirmedCount = 0)
        val decoded = codec.decode(setOf(codec.encode(entry)))
        assertEquals(entry, decoded.first())
    }

    @Test
    fun `解码：畸形行被忽略而非崩溃`() {
        val decoded = codec.decode(setOf("", "95526", "95526|1", "|||", "|1|2|3"))
        assertTrue("畸形行应被忽略，实际=$decoded", decoded.isEmpty())
    }

    @Test
    fun `解码：按发送方升序`() {
        val decoded = codec.decode(
            setOf(
                codec.encode(SmsWhitelistEntry("95588", true, 3)),
                codec.encode(SmsWhitelistEntry("95526", true, 3)),
                codec.encode(SmsWhitelistEntry("10690000", false, 1)),
            ),
        )
        assertEquals(listOf("10690000", "95526", "95588"), decoded.map { it.sender })
    }

    @Test
    fun `解码：计数非法回退为 0`() {
        val decoded = codec.decode(setOf("95526|1|abc|0"))
        assertEquals(0, decoded.first().confirmedCount)
    }
}
