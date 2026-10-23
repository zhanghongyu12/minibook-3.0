package com.minibook.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账本图标集守卫测试（2026-10-13，用户原话：「账本的图标也换一下」）。
 *
 * 与 [CategoryIconsTest] 同一做法：路径数据来自 Iconify 的 Material Symbols Rounded 公开 SVG，
 * `addPathNodes` 解析失败只在首帧抛异常（F-15o 教训），所以这里在 JVM 侧把
 * 「全部路径可解析」「key 唯一」「旧 emoji 兜底映射齐全」钉死。
 */
class LedgerIconsTest {

    @Test
    fun `所有账本图标都能解析且网格为 24`() {
        assertTrue(LedgerIcons.all.isNotEmpty())
        LedgerIcons.all.forEach { vector ->
            assertEquals("viewport 宽度应为 24", 24f, vector.viewportWidth, 0f)
            assertEquals("viewport 高度应为 24", 24f, vector.viewportHeight, 0f)
            assertTrue(
                "路径解析失败（root.size == 0）：${vector.name}",
                vector.root.size > 0,
            )
        }
    }

    @Test
    fun `key 唯一且 resolve 走 key 与旧 emoji 兜底`() {
        val keys = LedgerIcons.allKeys
        assertEquals("key 列表应与矢量一一对应", keys.size, LedgerIcons.all.size)
        assertEquals("key 不得重复", keys.size, keys.toSet().size)

        // 旧 emoji 必须能兜底到同一个矢量（16 个编辑器历史 emoji 全部有映射）
        assertEquals(16, LedgerIcons.legacyEmojiCount)
        val book = LedgerIcons.resolve(LedgerIcons.BOOK)
        assertNotNull("BOOK key 必须可解析", book)
        assertTrue(
            "旧 emoji 📒 应兜底到 BOOK 矢量",
            LedgerIcons.resolve("📒") === book,
        )
    }

    @Test
    fun `未知串给空而不崩`() {
        assertTrue(LedgerIcons.resolve("") == null)
        assertTrue(LedgerIcons.resolve("不存在的图标") == null)
    }
}
