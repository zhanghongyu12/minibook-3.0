package com.minibook.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类图标集守卫测试（2026-10-12）。
 *
 * 路径数据来自 Iconify 的 Material Symbols Rounded 公开 SVG；`addPathNodes` 解析失败
 * 只在首帧抛异常（F-15o 教训：编译绿、单测绿仍运行时崩），所以这里在 JVM 侧把
 * 「全部路径可解析」「key 唯一」「旧 emoji 兜底映射齐全」钉死。
 */
class CategoryIconsTest {

    @Test
    fun `所有分类图标都能解析且网格为 24`() {
        assertTrue(CategoryIcons.all.isNotEmpty())
        CategoryIcons.all.forEach { vector ->
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
        val keys = CategoryIcons.allKeys
        assertEquals("key 列表应与矢量一一对应", keys.size, CategoryIcons.all.size)
        assertEquals("key 不得重复", keys.size, keys.toSet().size)

        // 旧 emoji 必须能兜底到同一个矢量
        assertEquals(24, CategoryIcons.legacyEmojiCount)
        val food = CategoryIcons.resolve(CategoryIcons.FOOD)
        assertNotNull("FOOD key 必须可解析", food)
        assertTrue(
            "旧 emoji 🍜 应兜底到 FOOD 矢量",
            CategoryIcons.resolve("🍜") === food,
        )
    }

    @Test
    fun `未知串给空而不崩`() {
        assertTrue(CategoryIcons.resolve("") == null)
        assertTrue(CategoryIcons.resolve("不存在的图标") == null)
    }

    @Test
    fun `礼金与红包图标不再相同`() {
        // 新增的「礼金」专用矢量：既不是礼盒（GIFT）也不是红包（REDPACKET）。
        val giftMoney = CategoryIcons.resolve(CategoryIcons.GIFT_MONEY)
        assertNotNull("GIFT_MONEY key 必须可解析", giftMoney)
        assertTrue("礼金矢量不得与红包矢量相同", giftMoney !== CategoryIcons.RedPacket)
        assertTrue("礼金矢量不得与礼盒矢量相同", giftMoney !== CategoryIcons.Gift)
        assertTrue(
            "picker 网格必须包含礼金图标",
            CategoryIcons.pickerKeys.contains(CategoryIcons.GIFT_MONEY),
        )
        assertEquals("key 仍不得重复", CategoryIcons.allKeys.size, CategoryIcons.allKeys.toSet().size)
    }

    @Test
    fun `名为礼金的旧分类换成礼金矢量 其它分类不受影响`() {
        // 历史账本里「礼金」存的是 🧧 或 "redpacket"（与自建「红包」撞图）——按名字改名到礼金矢量。
        assertTrue(
            "旧 emoji 🧧 + 名称礼金 应得到礼金矢量",
            CategoryIcons.resolve("🧧", "礼金") === CategoryIcons.GiftMoney,
        )
        assertTrue(
            "旧 key redpacket + 名称礼金 应得到礼金矢量",
            CategoryIcons.resolve(CategoryIcons.REDPACKET, "礼金") === CategoryIcons.GiftMoney,
        )
        assertTrue(
            "礼盒 + 名称礼金 应得到礼金矢量",
            CategoryIcons.resolve(CategoryIcons.GIFT, "礼金") === CategoryIcons.GiftMoney,
        )
        // 名字不是礼金时保持原样：红包仍旧是红包、礼盒仍旧是礼盒。
        assertTrue(
            "红包分类的 🧧 仍应是红包矢量",
            CategoryIcons.resolve("🧧", "红包") === CategoryIcons.RedPacket,
        )
        assertTrue(
            "无名称时不做改名",
            CategoryIcons.resolve("🧧") === CategoryIcons.RedPacket,
        )
    }
}
